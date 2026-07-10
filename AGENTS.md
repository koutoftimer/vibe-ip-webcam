# AGENTS.md

## Project

Single-module Android app (`com.vibe.ipwebcam`). Turns the phone camera into an IP webcam server over MJPEG/HTTP. A companion Linux script bridges the stream to a virtual V4L2 device.

No tests, no CI, no lint config beyond Gradle defaults.

## Build

```bash
# No gradlew in repo — generate it first (requires Gradle installed)
gradle wrapper

# Build
./gradlew assembleDebug

# Install
./gradlew installDebug
```

AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01, Gradle 8.11.1. Java 17 target. compileSdk 35, minSdk 26.

## Architecture

6 Kotlin files, all under `app/src/main/java/com/vibe/ipwebcam/`:

| File | Role |
|------|------|
| `MainActivity.kt` | Compose UI: status indicator, resolution/FPS/port settings, start/stop button. Binds to StreamService. |
| `StreamService.kt` | Foreground service. Owns CameraController + MjpegServer. Acquires PARTIAL_WAKE_LOCK. |
| `CameraController.kt` | Camera2 API wrapper. Captures JPEG frames via `ImageReader`, pushes to MjpegServer via callback. |
| `MjpegServer.kt` | Raw `ServerSocket` HTTP server (zero external deps). Serves MJPEG at `/video`, status HTML at `/`. |
| `Prefs.kt` | SharedPreferences wrapper for port/width/height/fps. |
| `App.kt` | Creates notification channel on startup. |

Frame flow: `Camera2 → ImageReader (JPEG) → MjpegServer.pushFrame() → clients read from volatile frameId-guarded latestFrame`

## Gotchas

- **MjpegServer is NOT NanoHTTPD.** It's a custom `ServerSocket` implementation with zero dependencies. Each MJPEG client gets its own thread; frames are broadcast via `@Volatile` + `synchronized(frameLock)`. Don't add NanoHTTPD or other HTTP libs.
- **Camera2 callbacks run on a dedicated HandlerThread** (`CameraThread`), not the main thread. Don't move ImageReader setup off that thread.
- **Cleartext HTTP is allowed** via `network_security_config.xml` (required since the server is HTTP, not HTTPS).
- **Foreground service type is `camera`** — required on Android 14+ (API 34). The manifest already declares `FOREGROUND_SERVICE_CAMERA` permission.
- **No gradlew script in repo** — only `gradle/wrapper/gradle-wrapper.properties` exists. Must run `gradle wrapper` or open in Android Studio to generate it.
- **XML theme uses `android:Theme.Material.Light.NoActionBar`** (built-in). Don't change to `Theme.Material3.*` without adding the View-based `com.google.android.material:material` dependency — the Compose Material3 library doesn't provide XML themes.
- **ProGuard is enabled for release** (`isMinifyEnabled = true`) but `proguard-rules.pro` is empty. If you add reflection-based code, add keep rules.

## Linux Companion

`start_cam.sh` at repo root loads `v4l2loopback` and bridges the stream:
```bash
sudo apt install ffmpeg v4l2loopback-utils
./start_cam.sh  # edit ANDROID_IP first
```
Requires the `v4l2loopback` kernel module. Not all distros ship it — may need `modprobe` manually.
