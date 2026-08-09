@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

if "%JAVA_HOME%"=="" (
  if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot"
  )
)

set "ARGS=--radar.tail-from-end=true"
if not "%HYBRIS_HOME%"=="" (
  set "ARGS=!ARGS! --radar.hybris-home=%HYBRIS_HOME%"
) else (
  echo HYBRIS_HOME env is not set. Using radar.hybris-home from application.properties.
  echo If that property is also empty, collector will replay sample-logs ^(DEMO^).
)
if not "%RADAR_PREFIX%"=="" (
  set "ARGS=!ARGS! --radar.custom-package-prefix=%RADAR_PREFIX%"
)

start "commerce-error-radar-collector" cmd /k "cd /d "%~dp0" && mvn -pl collector -am spring-boot:run "-Dspring-boot.run.arguments=!ARGS!""
start "commerce-error-radar-ui" cmd /k "cd /d "%~dp0web" && npm start"

echo Waiting for the UI...
timeout /t 10 /nobreak >nul
start "" http://localhost:4200
echo Opened http://localhost:4200
endlocal
