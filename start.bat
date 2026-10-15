@echo off
chcp 65001 >nul
title Knowledge Base Launcher

echo ========================================
echo   Personal Knowledge Base - Launcher
echo ========================================
echo.

cd /d "%~dp0"

REM Check backend port 8081
netstat -ano | findstr ":8081" | findstr "LISTENING" >nul
if %errorlevel% equ 0 (
    echo [OK] Backend already running on port 8081
) else (
    echo [1/3] Starting backend [Spring Boot]...
    start "KB-Backend" /D "%~dp0" cmd /k "mvnw.cmd spring-boot:run"
    echo       Backend is starting, please wait 15-30 seconds...
)
echo.

REM Check frontend port 5173
netstat -ano | findstr ":5173" | findstr "LISTENING" >nul
if %errorlevel% equ 0 (
    echo [OK] Frontend already running on port 5173
) else (
    echo [2/3] Starting frontend [Vite]...
    start "KB-Frontend" /D "%~dp0frontend" cmd /k "npm run dev"
    echo       Frontend is starting...
)
echo.

REM Wait for backend ready, then open browser
echo [3/3] Waiting for backend ready...
set /a wait_count=0
:wait_loop
timeout /t 3 /nobreak >nul
set /a wait_count+=1
netstat -ano | findstr ":8081" | findstr "LISTENING" >nul
if %errorlevel% equ 0 goto open_browser
if %wait_count% geq 20 goto open_browser
goto wait_loop

:open_browser
echo.
echo Opening browser...
start http://localhost:5173
echo.
echo ========================================
echo   Done!
echo   Frontend: http://localhost:5173
echo   Backend:  http://localhost:8081
echo ========================================
echo.
echo Tip: Close the two black windows to stop services
pause