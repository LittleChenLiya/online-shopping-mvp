@echo off
setlocal
cd /d "%~dp0.."
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\test.ps1
if errorlevel 1 exit /b 1
pause
