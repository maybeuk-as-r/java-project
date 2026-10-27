#!/usr/bin/env bash
set -e
mkdir -p out
javac -d out src/AttendanceAnalytics.java
java -cp out AttendanceAnalytics
