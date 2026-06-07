@echo off
setlocal
cd /d "%~dp0frontend"
echo Starting Auto HR frontend on http://localhost:3000 ...
if not exist "node_modules" (
  echo Installing frontend dependencies ...
  call npm.cmd install
)
call npm.cmd run dev -- --host 0.0.0.0
pause
