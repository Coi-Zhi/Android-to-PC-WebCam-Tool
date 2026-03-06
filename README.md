# PWebCam

PWebCam is a lightweight Android-to-PC USB webcam pipeline.

It captures the Android phone camera on the phone side, sends frames to the PC through **USB + ADB forward**, and displays it on the PC as a webcam source.

Compared with traditional wireless webcam apps, this solution provides:

- USB direct connection
- Lower latency
- Better tracking stability
- No watermark
- Free and open workflow

---

## Features

- Android front camera capture
- USB direct transport through ADB
- Python receiver on PC
- Virtual webcam output on PC
- Works well with face tracking / VTuber workflows

---

## Project Structure

```text
PWebCam/
├─ AndroidApp/          # Android Studio project
├─ webcam.py            # PC receiver
├─ run.bat              # One-click startup script
└─ README.md
