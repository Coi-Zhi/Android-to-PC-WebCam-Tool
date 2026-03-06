@echo off
cd /d %~dp0

echo Removing old adb forwards...
adb forward --remove-all

echo Creating adb forward tcp:9000 -> tcp:9000 ...
adb forward tcp:9000 tcp:9000

echo Starting Python webcam receiver...
python webcam.py

pause