@echo off
cd /d "%~dp0"
title Minecraft Music Player - Server
call gradlew.bat runServer
pause
