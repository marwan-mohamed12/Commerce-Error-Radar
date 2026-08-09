@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

if "%JAVA_HOME%"=="" (
  if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot"
  )
)

set "ARGS=--radar.tail-from-end=true"
if not "%HYBRIS_HOME%"=="" set "ARGS=!ARGS! --radar.hybris-home=%HYBRIS_HOME%"
if not "%RADAR_PREFIX%"=="" set "ARGS=!ARGS! --radar.custom-package-prefix=%RADAR_PREFIX%"

if "%HYBRIS_HOME%"=="" (
  echo No HYBRIS_HOME — demo mode will replay ..\sample-logs
)

mvn -pl collector -am spring-boot:run "-Dspring-boot.run.arguments=!ARGS!"
endlocal
