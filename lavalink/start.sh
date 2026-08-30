#!/usr/bin/env bash
if [ -d "../.git" ]; then
    git pull
fi
java -jar Lavalink.jar
