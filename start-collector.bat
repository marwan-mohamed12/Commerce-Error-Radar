@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

if "%JAVA_HOME%"=="" (
  if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot"
  )
)

set "FILE_HOME="
set "FILE_PREFIX="
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("collector\src\main\resources\application.properties") do (
  if /i "%%A"=="radar.hybris-home" set "FILE_HOME=%%B"
  if /i "%%A"=="radar.custom-package-prefix" set "FILE_PREFIX=%%B"
)

set "ARGS=--radar.tail-from-end=true"
set "MODE=DEMO"
if not "%HYBRIS_HOME%"=="" (
  set "ARGS=!ARGS! --radar.hybris-home=%HYBRIS_HOME%"
  echo LIVE: %HYBRIS_HOME%  [HYBRIS_HOME env]
  set "MODE=LIVE"
) else (
  echo !FILE_HOME! | findstr /c:"${" >nul
  if errorlevel 1 if not "!FILE_HOME!"=="" (
    echo LIVE: !FILE_HOME!  [application.properties]
    set "MODE=LIVE"
  )
)
if "!MODE!"=="DEMO" (
  echo DEMO: radar.hybris-home is empty. Replaying sample-logs.
  echo Set radar.hybris-home in application.properties, or: set HYBRIS_HOME=D:/path/to/hybris
)

if not "%RADAR_PREFIX%"=="" (
  set "ARGS=!ARGS! --radar.custom-package-prefix=%RADAR_PREFIX%"
) else if not "!FILE_PREFIX!"=="" (
  echo Prefix: !FILE_PREFIX!
)

mvn -pl collector -am spring-boot:run "-Dspring-boot.run.arguments=!ARGS!"
endlocal
