@echo off
echo Resetting all booking data...
del /f /q bookings_node8081.json 2>nul
del /f /q bookings_node8082.json 2>nul
echo Done! All bookings have been cleared.
echo Restart the servers to begin with fresh data.
pause
