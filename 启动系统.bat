@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

rem Prefer the Python installation already present on this computer.
if exist "D:\Python\python.exe" (
    "D:\Python\python.exe" -X utf8 start.py %*
    goto :finish
)

rem Other computers can use the Python launcher or their PATH installation.
where py >nul 2>nul
if not errorlevel 1 (
    py -3 -X utf8 start.py %*
    goto :finish
)
where python >nul 2>nul
if not errorlevel 1 (
    python -X utf8 start.py %*
    goto :finish
)
echo Python 3.10+ was not found. Install Python or check its installation path.

:finish
pause
endlocal
