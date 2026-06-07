@echo off
setlocal
cd /d "%~dp0"
echo Starting Auto HR backend and frontend ...
start "Auto HR Backend" "%~dp0start-backend.bat"
start "Auto HR Frontend" "%~dp0start-frontend.bat"
echo Backend:  http://localhost:8080
echo Frontend: http://localhost:3000
pause
