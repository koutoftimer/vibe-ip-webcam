# AGENTS.md

## Project

Single-module Android app (`ua.pp.ruslan_kovtun.ipwebcam`). Turns the phone camera into an IP webcam server over MJPEG/HTTP. A companion Linux script bridges the stream to a virtual V4L2 device.

## Build & Test

```bash
./gradlew assembleDebug        # build
./gradlew installDebug          # install on connected device
./gradlew testDebugUnitTest     # unit tests (JVM, no device needed)
```

AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01, Gradle 8.11.1. Java 17 target. compileSdk 35, minSdk 26.

CI: `.github/workflows/release.yml` — triggered on `v*` tags. Builds signed release APK and uploads to GitHub Releases. Requires `KEYSTORE_BASE64` as a GitHub **Environment** secret (not repo secret). Signing config in `app/build.gradle.kts` reads `KEYSTORE_FILE`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` from env vars; release is unsigned when vars are absent.

## Architecture

9 Kotlin files under `app/src/main/java/ua/pp/ruslan_kovtun/ipwebcam/`:

| File | Role |
|------|------|
| `MainActivity.kt` | Compose UI: status indicator, resolution/FPS/port settings, start/stop button. Binds to StreamService. |
| `StreamService.kt` | Foreground service. Owns CameraController + MjpegServer + FrameBufferPool. Acquires PARTIAL_WAKE_LOCK. |
| `CameraController.kt` | Camera2 API wrapper. Captures JPEG frames via `ImageReader`, pushes to MjpegServer via callback. |
| `MjpegServer.kt` | Raw `ServerSocket` HTTP server (zero external deps). Serves MJPEG at `/video`, status HTML at `/`. |
| `FrameBufferPool.kt` | Refcounted ByteArray pool. Avoids per-frame heap allocations. |
| `Frame.kt` | Simple holder: `class Frame(val bytes: ByteArray, val length: Int)`. |
| `FpsRangePicker.kt` | Pure logic: picks best AE target FPS range from Camera2 capabilities. |
| `Prefs.kt` | SharedPreferences wrapper for port/width/height/fps. |
| `App.kt` | Creates notification channel on startup. |

Frame flow: `Camera2 → ImageReader (JPEG) → FrameBufferPool.acquire() → MjpegServer.pushFrame() → clients read from volatile frameId-guarded latestFrame`

Tests (JVM-only, no Android deps): `FrameBufferPoolTest.kt`, `FpsRangePickerTest.kt`.

## Gotchas

- **MjpegServer is NOT NanoHTTPD.** Custom `ServerSocket` implementation with zero dependencies. Each MJPEG client gets its own thread; frames are broadcast via `@Volatile` + `synchronized(frameLock)`. Don't add NanoHTTPD or other HTTP libs.
- **Camera2 callbacks run on a dedicated HandlerThread** (`CameraThread`), not the main thread. Don't move ImageReader setup off that thread.
- **Buffer pool refcount protocol must be exact.** `FrameBufferPool` tracks refCounts per buffer. An unbalanced retain/release causes silent buffer leaks → OOM after minutes of streaming. The lifecycle is: `acquire(1)` → `serveMjpeg retain(+1)` → `serveMjpeg release(-1)` → `next pushFrame release(-1)` → back to pool. Never add a retain without a matching release path.
- **Cleartext HTTP is allowed** via `network_security_config.xml` (required since the server is HTTP, not HTTPS).
- **Foreground service type is `camera`** — required on Android 14+ (API 34). The manifest already declares `FOREGROUND_SERVICE_CAMERA` permission.
- **XML theme uses `android:Theme.Material.Light.NoActionBar`** (built-in). Don't change to `Theme.Material3.*` without adding the View-based `com.google.android.material:material` dependency — the Compose Material3 library doesn't provide XML themes.
- **ProGuard is enabled for release** (`isMinifyEnabled = true`) but `proguard-rules.pro` is empty. If you add reflection-based code, add keep rules.

## Linux Companion

`start_cam.sh` at repo root loads `v4l2loopback` and bridges the stream:
```bash
sudo apt install ffmpeg v4l2loopback-utils
./start_cam.sh  # edit ANDROID_IP first
```
Requires the `v4l2loopback` kernel module. Not all distros ship it — may need `modprobe` manually.
