@echo off
echo Starting Node 1 (COORDINATOR) on port 8081...
java -cp out BookingServer --port 8081 --role coordinator --workers http://localhost:8082
pause
