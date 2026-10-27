#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p lib out
JAR="lib/mariadb-java-client-3.5.3.jar"
if [ ! -f "$JAR" ]; then
  echo "Downloading MariaDB JDBC driver..."
  curl -fL "https://repo1.maven.org/maven2/org/mariadb/jdbc/mariadb-java-client/3.5.3/mariadb-java-client-3.5.3.jar" -o "$JAR"
fi
echo "Compiling full Smart Attendance GUI..."
javac -cp "$JAR" -d out src/AttendanceAnalytics.java
echo "Launching GUI connected to smart_attendance..."
java -cp "out:$JAR" AttendanceAnalytics
