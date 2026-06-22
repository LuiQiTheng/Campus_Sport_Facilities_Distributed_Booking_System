@echo off
echo Starting Node 2 (WORKER) on port 8082...
java -cp out BookingServer --port 8082 --role worker --coordinator http://10.18.156.13:8081
pause
