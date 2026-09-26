@echo off
chcp 65001 >nul
title 知识库 - 停止服务

echo ========================================
echo   个人知识库 - 停止服务
echo ========================================
echo.

echo 正在查找并停止后端进程 (端口 8081)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8081" ^| findstr "LISTENING"') do (
    taskkill /PID %%a /F >nul 2>&1
    echo   已停止进程 PID: %%a
)

echo 正在查找并停止前端进程 (端口 5173)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":5173" ^| findstr "LISTENING"') do (
    taskkill /PID %%a /F >nul 2>&1
    echo   已停止进程 PID: %%a
)

echo.
echo ========================================
echo   服务已停止
echo ========================================
echo.
pause