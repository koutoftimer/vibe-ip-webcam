# IP Webcam - Android to Linux via V4L2

Turns your Android phone's camera into an IP webcam, viewable on Linux as a V4L2 device.

## How It Works

1. **Android app** captures camera frames (Camera2 API) and serves them as MJPEG over HTTP
2. **Linux** consumes the MJPEG stream and maps it to a virtual V4L2 device via ffmpeg

## Build

Open in Android Studio or build from command line:

```bash
# Generate gradle wrapper (if not present)
gradle wrapper

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

## Usage

### Android Side
1. Install and launch the app
2. Grant camera permission
3. Configure resolution, FPS, and port (defaults: 640x480, 15fps, port 8080)
4. Tap **Start Streaming**
5. Note the IP address shown on screen

### Linux Side

Install dependencies:

```bash
sudo apt update
sudo apt install ffmpeg v4l2loopback-utils
```

Use the included script (edit `ANDROID_IP` in `start_cam.sh` first):

```bash
./start_cam.sh
```

Or run manually:

```bash
# Load the virtual camera module
sudo modprobe v4l2loopback video_nr=1 card_label="Android-IP-Cam" exclusive_caps=1

# Bridge the stream
ffmpeg -hide_banner -loglevel error \
    -probesize 32 -analyzeduration 0 \
    -i http://<phone-ip>:8080/video \
    -vf format=yuv420p \
    -f v4l2 /dev/video1
```

### Verify V4L2 Device

```bash
# List video devices
ls -la /dev/video*

# Check device info
v4l2-ctl --device=/dev/video1 --list-formats-ext

# Test capture
ffplay /dev/video1
```

## Battery Optimization

- Camera runs only while streaming is active
- HTTP server sends frames only when clients are connected
- Partial wake lock keeps CPU alive when screen is off
- Stop streaming to fully release all resources

## Architecture

```
Phone Camera (Camera2) → JPEG → MjpegServer (HTTP:8080)
                                      ↑
Linux ffmpeg ←──── HTTP/MJPEG ────────┘
     ↓
/dev/video1 (V4L2 virtual device)
```

## Requirements

- **Android**: 8.0+ (API 26), back camera
- **Linux**: ffmpeg, v4l2loopback-utils (`apt install ffmpeg v4l2loopback-utils`)

## Permissions

| Permission | Purpose |
|-----------|---------|
| `CAMERA` | Access phone camera |
| `FOREGROUND_SERVICE` | Keep streaming when app is backgrounded |
| `FOREGROUND_SERVICE_CAMERA` | Camera foreground service (Android 14+) |
| `WAKE_LOCK` | Prevent CPU sleep during streaming |
| `INTERNET` | HTTP server |
