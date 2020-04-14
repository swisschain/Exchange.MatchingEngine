@echo off
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_HOME=%DIRNAME%..

set swisschain_ME_OPTS="-Dlog4j.configurationFile=file:///%APP_HOME%/cfg/log4j2.xml"
mkdir ..\log
call matching-engine.bat %APP_HOME%/cfg/application.properties > ..\log\out.log 2> ..\log\err.log