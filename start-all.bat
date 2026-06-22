@echo off
echo ================================================
echo   Starting Distributed Booking System
echo   Node 1 (Coordinator) on port 8081
echo   Node 2 (Worker) on port 8082
echo ================================================
echo.

start "Node 1 - Coordinator (8081)" cmd /k "cd /d %~dp0 && java -cp out BookingServer --port 8081 --role coordinator --workers http://10.18.156.240:8082"

timeout /t 2 /nobreak >nul

start "Node 2 - Worker (8082)" cmd /k "cd /d %~dp0 && java -cp out BookingServer --port 8082 --role worker --coordinator http://10.18.156.13:8081"

echo Both nodes started!
echo   User A: Open http://10.18.156.240:8081 in browser
echo   User B: Open http://10.18.156.13:8082 in browser
echo.
pause
