@echo off
:: ─────────────────────────────────────────────────────────────────────────────
:: ATG Windows installer builder  →  produces a .exe installer
:: Requirements: JDK 17+, WiX Toolset 3.x (https://wixtoolset.org/releases/)
::               Maven (mvnw.cmd available)
:: Usage: Double-click or run from Command Prompt in the project root
:: ─────────────────────────────────────────────────────────────────────────────
setlocal EnableDelayedExpansion

set APP_NAME=ATG
set APP_VERSION=1.0.0
set JAR_NAME=AutomatedTimetable-%APP_VERSION%.jar
set JAR_PATH=target\%JAR_NAME%
set ICON=src\main\jpackage\atg-icon.ico
set OUTPUT=installer-output

:: Unique GUID — do NOT change after first release (used for upgrade detection)
set UPGRADE_UUID=c7a2f8e1-4b3d-4e9a-8f2c-1d5e6a7b8c9d

echo ==================================================
echo  ATG Installer Build  --  Windows (.exe)
echo ==================================================

:: ── Check jpackage ───────────────────────────────────────────────────────────
where jpackage >nul 2>&1
if errorlevel 1 (
    echo ERROR: jpackage not found. Add JDK 17+ bin to your PATH.
    pause
    exit /b 1
)

:: ── Step 1: Build fat JAR ────────────────────────────────────────────────────
echo.
echo Step 1/2 -- Building Spring Boot fat JAR...
call mvnw.cmd clean package -DskipTests -q
if errorlevel 1 (
    echo ERROR: Maven build failed. See output above.
    pause
    exit /b 1
)
echo          JAR: %JAR_PATH%

if not exist "%JAR_PATH%" (
    echo ERROR: JAR not found at %JAR_PATH%
    pause
    exit /b 1
)

:: ── Step 2: Package with jpackage ────────────────────────────────────────────
echo.
echo Step 2/2 -- Running jpackage...
if not exist "%OUTPUT%" mkdir "%OUTPUT%"

jpackage ^
  --type exe ^
  --name "%APP_NAME%" ^
  --app-version "%APP_VERSION%" ^
  --input target ^
  --main-jar "%JAR_NAME%" ^
  --icon "%ICON%" ^
  --dest "%OUTPUT%" ^
  --vendor "APCOER IT Dept" ^
  --description "Automated Timetable Generator" ^
  --copyright "2025 APCOER" ^
  --win-dir-chooser ^
  --win-shortcut ^
  --win-shortcut-prompt ^
  --win-menu ^
  --win-menu-group "APCOER" ^
  --win-upgrade-uuid "%UPGRADE_UUID%" ^
  --java-options "-Dspring.profiles.active=prod" ^
  --java-options "-Xverify:none" ^
  --java-options "-XX:TieredStopAtLevel=1" ^
  --java-options "-Xshare:off" ^
  --java-options "-Xmx512m"

if errorlevel 1 (
    echo.
    echo ERROR: jpackage failed.
    echo If you see "WiX not found", install WiX Toolset 3.x from:
    echo   https://github.com/wixtoolset/wix3/releases
    pause
    exit /b 1
)

echo.
echo ==================================================
echo  Build complete!
echo  Installer: %OUTPUT%\
dir /b "%OUTPUT%"
echo ==================================================
echo.
echo The installer embeds a full JRE -- no Java required on the target machine.
echo Database is stored at: %%USERPROFILE%%\.atg\atg.mv.db
pause
