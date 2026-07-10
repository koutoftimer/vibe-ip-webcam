# IP Webcam — Turn Your Phone into a Network Camera

This app uses your Android phone's camera as a webcam that streams video over
your local network. On the Linux side, a companion script makes it appear as a
regular `/dev/video` device — usable with Chrome, OBS, Zoom, or any V4L2
application.

---

## What You Need

| Item | Notes |
|------|-------|
| Android phone | Android 8.0+ (most phones from 2017 onward) with a rear camera |
| USB cable | To connect the phone to your computer for the first time |
| Linux PC | Ubuntu/Debian/Fedora — or any distro with `ffmpeg` and kernel module support |
| Wi-Fi | Phone and PC must be on the **same network** |
| ~30 minutes | For the initial setup |

---

## Step 1: Install Android Studio

Android Studio is the official tool for building Android apps. You only need it
to build this app once — after that you can uninstall it if you want.

### Download & Install

1. Go to **https://developer.android.com/studio**
2. Download the installer for your OS (Windows / macOS / Linux)
3. Run the installer and follow the prompts — the defaults are fine
4. On first launch, Android Studio will download the Android SDK (~2-3 GB). Let it finish.

### Verify

When you see the "Welcome to Android Studio" screen, you're ready.

---

## Step 2: Enable USB Debugging on Your Phone

This lets your computer talk to the phone for app installation.

1. Open **Settings** on your phone
2. Go to **About Phone** (or **About Device**)
3. Tap **Build Number** 7 times rapidly — you'll see "You are now a developer!"
4. Go back to **Settings** → **System** → **Developer Options**
5. Enable **USB Debugging**
6. Confirm the dialog when it appears

### First Connection

1. Plug your phone into your computer with the USB cable
2. On your phone, tap **Allow** when asked "Allow USB debugging?"
3. (If you don't see this prompt, disconnect and reconnect the cable)

---

## Step 3: Build & Install the App

### Option A: Using Android Studio (Recommended for Beginners)

1. Open Android Studio
2. Click **Open** (or **File → Open**)
3. Navigate to the `vibe-ip-web-cam` folder and click **Open**
4. Wait for the project to load (Gradle will sync — this takes a minute)
5. If prompted to install any SDK components, click **Install** or **Accept**
6. At the top toolbar, make sure your phone is selected in the device dropdown
7. Click the green **Run ▶** button
8. The app will build and install on your phone automatically

### Option B: Using the Command Line

Open a terminal in the project folder.

```bash
# Build the APK (this compiles the app into an installable file)
./gradlew assembleDebug

# Install it on your connected phone
./gradlew installDebug
```

If `./gradlew` doesn't exist or says "permission denied":

```bash
# Generate the gradle wrapper first
gradle wrapper

# Then build and install
./gradlew assembleDebug
./gradlew installDebug
```

> **If `gradle` is not installed:** Install it with your package manager:
> - Ubuntu/Debian: `sudo apt install gradle`
> - Fedora: `sudo dnf install gradle`
> - macOS: `brew install gradle`

#### Building a Signed Release APK

To build a signed release APK from the command line, generate a keystore
and pass the signing credentials as environment variables:

```bash
# Generate a keystore (once)
keytool -genkeypair -v -keystore ./release.jks -alias mykey \
  -keyalg RSA -keysize 2048 -validity 10000

# Build signed release
KEYSTORE_FILE=./release.jks \
KEYSTORE_PASSWORD=<password> \
KEY_ALIAS=mykey \
KEY_PASSWORD=<password> \
./gradlew assembleRelease
```

The signed APK is at `app/build/outputs/apk/release/app-release.apk`.

### Option C: Install Without a Computer (APK Transfer)

If you already have the built APK file (someone sent it to you, etc.):

1. Copy the APK file to your phone (via USB, Bluetooth, or cloud storage)
2. On the phone, open a file manager and tap the APK file
3. If prompted, enable **Install from Unknown Sources** for your file manager
4. Tap **Install**

> The APK file is located at `app/build/outputs/apk/debug/app-debug.apk` after building.

### Option D: Download Pre-built APK (Easiest)

Download the latest signed APK from [GitHub Releases](../../releases):

1. Go to the **Releases** page
2. Download the `.apk` file from the latest release
3. Transfer it to your phone and tap to install

---

## Step 4: Use the App

1. Open the **IP Webcam** app on your phone
2. Grant the **Camera permission** when asked
3. Configure settings (all optional — defaults work fine):
   - **Resolution**: 640×480 (fast) or 1280×720 (HD)
   - **Frame Rate**: 10–30 fps
   - **HTTP Port**: 8080 (default)
4. Tap **Start Streaming**
5. The app shows your phone's **IP address** and **port** — note these down

### What the App Shows While Streaming

- A green dot and "Streaming" status
- Your phone's IP address (e.g. `192.168.0.53`)
- Number of connected clients
- Current resolution and frame rate
- A Linux command you can copy

### Verifying from a Browser

On any device on the same network, open:

```
http://<phone-ip>:8080
```

You'll see a status page. The live video stream is at:

```
http://<phone-ip>:8080/video
```

Open this URL in VLC: **Media → Open Network Stream → enter the URL**.

---

## Step 5: Linux Companion — Virtual Camera Device

This makes the stream appear as `/dev/video1` on your Linux PC, so apps like
Chrome, OBS, and Zoom can use it as a webcam.

### Install Dependencies

```bash
sudo apt update
sudo apt install ffmpeg v4l2loopback-utils
```

For Fedora/RHEL:

```bash
sudo dnf install ffmpeg v4l2loopback-dkms
```

### Option A: Use the Included Script

1. Edit `start_cam.sh` and set your phone's IP address:

```bash
ANDROID_IP="192.168.0.53"   # <-- change this
PORT="8080"                  # <-- must match the port in the app
```

2. Run it:

```bash
chmod +x start_cam.sh
./start_cam.sh
```

Press `Ctrl+C` to stop.

### Option B: Run Manually

```bash
# Load the virtual camera kernel module
sudo modprobe v4l2loopback video_nr=1 card_label="Android-IP-Cam" exclusive_caps=1

# Bridge the MJPEG stream to the virtual device
ffmpeg -hide_banner -loglevel error \
    -probesize 32 -analyzeduration 0 \
    -i http://<phone-ip>:8080/video \
    -vf format=yuv420p \
    -f v4l2 /dev/video1
```

### Verify It Works

```bash
# Check that the device exists
ls -la /dev/video*

# See supported formats
v4l2-ctl --device=/dev/video1 --list-formats-ext

# Preview the feed
ffplay /dev/video1
```

### Use with Applications

- **Chrome/Meet/Zoom**: Select "Android-IP-Cam" as the camera in video settings
- **OBS**: Add a "Video Capture Device" source → select "Android-IP-Cam"
- **Any V4L2 app**: Use `/dev/video1`

---

## Troubleshooting

### "No devices found" / `adb devices` shows nothing

- Make sure USB debugging is enabled (Step 2)
- Try a different USB cable (some cables are charge-only)
- On the phone, revoke USB debugging authorizations in Developer Options, then reconnect
- Make sure you tapped "Allow" on the phone's USB debugging prompt

### App installs but camera won't open

- Make sure you granted Camera permission (check Settings → Apps → IP Webcam → Permissions)
- Some phones have a second "Camera" permission toggle in Developer Options

### Can't connect from Linux (`Connection refused`)

- Phone and PC must be on the **same Wi-Fi network**
- Check the IP address shown in the app — it might change if you reconnect to Wi-Fi
- Make sure streaming is active (green dot)
- Try opening `http://<phone-ip>:8080` in a browser first
- Some routers block device-to-device traffic — check your router's "client isolation" setting

### `/dev/video1` doesn't exist after modprobe

- The `v4l2loopback` kernel module might not be installed. Check: `modinfo v4l2loopback`
- On newer Ubuntu/Debian, install it: `sudo apt install v4l2loopback-dkms`
- Try loading manually: `sudo modprobe v4l2loopback video_nr=1`

### ffmpeg exits immediately / black screen in ffplay

- The stream might not have started yet on the phone side — wait a few seconds
  after tapping "Start Streaming"
- Try the VLC test first (Step 4) to confirm the stream itself works
- Increase `probesize` in the ffmpeg command if needed: `-probesize 100000`

### Phone gets hot or drains battery fast

- This is expected — the camera and network streaming use significant resources
- Stop streaming when not in use
- Lower the resolution to 640×480 to reduce load

---

## Requirements Reference

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| Android version | 8.0 (API 26) | 12+ |
| Linux kernel | Any with v4l2loopback support | 5.x+ |
| ffmpeg | Any recent version | 5.0+ |
| Network | Same Wi-Fi LAN | 2.4 GHz or 5 GHz |

### App Permissions

| Permission | Why |
|-----------|-----|
| Camera | Access the phone camera |
| Internet | Run the HTTP server |
| Foreground Service | Keep streaming when the app is backgrounded |
| Foreground Service (Camera) | Required on Android 14+ |
| Wake Lock | Prevent CPU sleep during streaming |

---

## Architecture

```
Phone Camera (Camera2 API)
       │
       ▼
  ImageReader (JPEG frames)
       │
       ▼
  MjpegServer (HTTP:8080)
       │
       ▼  ← clients connect here
  Linux ffmpeg ←── HTTP/MJPEG
       │
       ▼
  /dev/video1 (V4L2 virtual device)
```

The app consists of 6 Kotlin files — a Camera2 wrapper, an MJPEG HTTP server
(zero dependencies), a foreground service, a settings UI built with Jetpack
Compose, and a preferences helper.

---

## Battery Notes

- The camera runs **only** while streaming is active
- The HTTP server sends frames **only** when at least one client is connected
- A partial wake lock keeps the CPU alive when the screen is off
- Stop streaming to fully release all resources
