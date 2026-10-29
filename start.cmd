@echo off
chcp 65001 >nul
setlocal

rem ============================================================
rem  PurchaseFlow one-click launcher (Windows)
rem  Backend only: http://localhost:8000
rem  For frontend dev server, run "npm run dev" in frontend\
rem ============================================================

set "BACKEND_DIR=%~dp0backend"
set "JAR=%BACKEND_DIR%\target\purchase-approval-1.0.0.jar"
set "TMP_DIR=%BACKEND_DIR%\target\tmp"
set "LOCAL_JDK=%~dp0..\.toolchain\jdk17\bin\java.exe"

if not exist "%JAR%" (
    echo [ERROR] Jar not found: %JAR%
    echo         Run: cd backend ^&^& mvn clean package -DskipTests
    pause
    exit /b 1
)

if not exist "%TMP_DIR%" mkdir "%TMP_DIR%"
cd /d "%BACKEND_DIR%"

rem Prefer JDK on PATH
where java >nul 2>nul
if %errorlevel%==0 (
    echo [INFO] Using java from PATH -^> http://localhost:8000
    java "-Djava.io.tmpdir=%TMP_DIR%" -jar "%JAR%"
    goto :done
)

rem Fallback to the portable JDK shipped next to the project
if exist "%LOCAL_JDK%" (
    echo [INFO] Using portable JDK -^> http://localhost:8000
    "%LOCAL_JDK%" "-Djava.io.tmpdir=%TMP_DIR%" -jar "%JAR%"
    goto :done
)

echo [ERROR] Java 17 not found.
echo         Install a JDK, or add java to PATH.
pause

:done
if errorlevel 1 pause
