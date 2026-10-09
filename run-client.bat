@echo off
cd /d "%~dp0"
title Minecraft Music Player - Client
call gradlew.bat runClient
pause
