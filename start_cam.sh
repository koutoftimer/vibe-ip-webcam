#!/bin/bash

# Configuration
ANDROID_IP="192.168.0.53"
PORT="8080"
DEVICE_NODE="/dev/video1"

# 1. Load the v4l2loopback module if not loaded
if ! lsmod | grep -q v4l2loopback; then
    sudo modprobe v4l2loopback video_nr=1 card_label="Android-IP-Cam" exclusive_caps=1
    echo "Loaded v4l2loopback module"
else
    echo "v4l2loopback already loaded"
fi

echo "Connecting to http://$ANDROID_IP:$PORT/video..."

# 2. Run FFmpeg to bridge the MJPEG stream to a virtual V4L2 device
#    -probesize 32 / -analyzeduration 0: minimize startup latency
#    scale+pad: force exact 720p (Chrome/Meet require standard resolutions)
#    format=yuv420p: pixel format expected by browsers and most V4L2 consumers
ffmpeg -hide_banner -loglevel error \
    -probesize 32 -analyzeduration 0 \
    -i "http://$ANDROID_IP:$PORT/video" \
    -vf "scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2,format=yuv420p" \
    -f v4l2 -vcodec rawvideo "$DEVICE_NODE"
