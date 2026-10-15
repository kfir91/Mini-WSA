@echo off
setlocal
cd /d "%~dp0.."

set COUNT=%~1
if "%COUNT%"=="" set COUNT=20
set RATE=%~2
if "%RATE%"=="" set RATE=10
set WAVE=%~3
if "%WAVE%"=="" set WAVE=sql
set CONFIG_ID=%~4
if "%CONFIG_ID%"=="" set CONFIG_ID=14227
set BASE_URL=%~5
if "%BASE_URL%"=="" set BASE_URL=http://localhost:8081
set BURST_SIZE=%~6
if "%BURST_SIZE%"=="" set BURST_SIZE=50

echo Building generator modules (quiet)...
call mvn -q -pl generator -am -DskipTests install
if errorlevel 1 exit /b %ERRORLEVEL%
echo Starting generator...
call mvn -q -pl generator -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.mainClass=com.miniwsa.generator.GeneratorMain" "-Dexec.args=--base-url %BASE_URL% --count %COUNT% --rate %RATE% --wave %WAVE% --config-id %CONFIG_ID% --burst-size %BURST_SIZE%"
exit /b %ERRORLEVEL%
