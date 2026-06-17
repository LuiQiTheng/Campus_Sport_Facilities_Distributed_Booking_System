@echo off
echo ================================================
echo   Compiling Distributed Booking System...
echo ================================================
echo.

if not exist "out" mkdir out

javac -d out src\*.java

if %ERRORLEVEL% EQU 0 (
    echo.
    echo   Compilation successful!
    echo   Output directory: out\
    echo.
    echo   To start the system, run:
    echo     .\start-node1.bat  [Coordinator]
    echo     .\start-node2.bat  [Worker]
    echo   Or run .\start-all.bat to start both.
    echo.
) else (
    echo.
    echo   Compilation FAILED. Please check the errors above.
    echo.
)

pause
