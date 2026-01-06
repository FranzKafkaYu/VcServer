@echo off
REM VcServer Windows Build Script
REM This script ensures Java 17+ is used and builds the release APK

echo === VcServer Windows Build Script ===
echo.

REM Check if Java is installed
where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Error: Java is not installed or not in PATH.
    echo Please install Java 17 or higher.
    echo Download from: https://adoptium.net/
    exit /b 1
)

REM Check Java version
echo Checking Java version...
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VERSION_STRING=%%g
    goto :found_version
)
:found_version

REM Extract major version number
for /f "tokens=1 delims=." %%a in ("%JAVA_VERSION_STRING%") do set MAJOR_VERSION=%%a
for /f "tokens=1 delims=." %%a in ("%MAJOR_VERSION:"=%") do set MAJOR_VERSION=%%a

echo Java version string: %JAVA_VERSION_STRING%
echo Major version: %MAJOR_VERSION%

REM Check if version is 17 or higher
if %MAJOR_VERSION% LSS 17 (
    echo.
    echo ERROR: Java 17 or higher is required. Current version: %MAJOR_VERSION%
    echo.
    echo Please install Java 17 or higher:
    echo   1. Download from: https://adoptium.net/
    echo   2. Install Java 17 (Temurin 17 LTS recommended)
    echo   3. Set JAVA_HOME environment variable to Java 17 installation path
    echo   4. Add %JAVA_HOME%\bin to PATH
    echo.
    exit /b 1
)

echo Java version check passed!
echo.

REM Display JAVA_HOME if set
if defined JAVA_HOME (
    echo JAVA_HOME: %JAVA_HOME%
) else (
    echo Warning: JAVA_HOME is not set. Using system default Java.
    echo.
    echo To set JAVA_HOME permanently:
    echo   1. Open System Properties ^(Win + Pause^)
    echo   2. Click "Advanced system settings"
    echo   3. Click "Environment Variables"
    echo   4. Add new System Variable:
    echo      Name: JAVA_HOME
    echo      Value: C:\Program Files\Java\jdk-17
    echo   5. Add %JAVA_HOME%\bin to PATH
    echo.
)

echo.
echo Stopping existing Gradle daemons...
call gradlew.bat --stop >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo No running daemons to stop.
)

echo.
echo Building release APK...
call gradlew.bat assembleRelease

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Build successful!
    echo ========================================
    echo.
    echo APK location: app\build\outputs\apk\release\
    if exist "app\build\outputs\apk\release\*.apk" (
        dir /b "app\build\outputs\apk\release\*.apk"
    )
    echo.
) else (
    echo.
    echo ========================================
    echo Build failed!
    echo ========================================
    echo.
    echo Troubleshooting:
    echo   1. Ensure Java 17+ is installed and JAVA_HOME is set correctly
    echo   2. Try: gradlew.bat --stop
    echo   3. Try: gradlew.bat clean
    echo   4. Check error messages above
    echo.
    exit /b 1
)

pause

