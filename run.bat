@echo off
setlocal
cd /d "%~dp0"

where mvn >nul 2>nul
if errorlevel 1 (
  echo Apache Maven is needed to run manager alerter
  pause
  exit /b 1
)

mvn -f "%~dp0pom.xml" exec:java
if errorlevel 1 pause