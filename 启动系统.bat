@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\start.ps1 %*
if errorlevel 1 echo Startup failed. Read the error above.
pause
