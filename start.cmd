@echo off
chcp 65001 >nul
setlocal

rem ============================================================
rem  PurchaseFlow one-click launcher
rem  Usage: double-click this file, or run start.cmd in a terminal
rem ============================================================

set "JAR=%~dp0target\purchase-approval-1.0.0.jar"
set "LOCAL_JDK=%~dp0..\.toolchain\jdk17\bin\java.exe"

if not exist "%JAR%" (
    echo [ERROR] Jar not found: %JAR%
    echo         Run "mvn clean package" first.
    pause
    exit /b 1
)

rem Prefer JDK on PATH
where java >nul 2>nul
if %errorlevel%==0 (
    echo [INFO] Using java from PATH
    java -jar "%JAR%"
    goto :done
)

rem Fallback to the portable JDK shipped next to the project
if exist "%LOCAL_JDK%" (
    echo [INFO] Using portable JDK: %LOCAL_JDK%
    "%LOCAL_JDK%" -jar "%JAR%"
    goto :done
)

echo [ERROR] Java 17 not found.
echo         Install a JDK, or add java to PATH.
pause

:done
if errorlevel 1 pause
