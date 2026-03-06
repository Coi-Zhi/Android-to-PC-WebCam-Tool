@echo off
cd /d %~dp0

echo Removing old adb forwards...
.\adb.exe forward --remove-all

echo Creating adb forward tcp:9000 -> tcp:9000 ...
.\adb.exe forward tcp:9000 tcp:9000

echo Starting webcam receiver...
.\webcam.exe

pause
