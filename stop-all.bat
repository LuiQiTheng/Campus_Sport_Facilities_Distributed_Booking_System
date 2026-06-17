@echo off
echo ================================================
echo   Stopping Distributed Booking System Nodes...
echo ================================================
taskkill /f /im java.exe 2>nul
echo.
echo All Java server nodes have been terminated.
pause
