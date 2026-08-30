@echo off
title Lavalink Server with All-In-One Plugin
if exist ..\.git (
    git pull
)
java -jar Lavalink.jar
pause
