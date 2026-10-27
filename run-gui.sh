#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p lib out
DBJAR="lib/mariadb-java-client-3.5.3.jar"
BCRYPT="lib/jbcrypt-0.4.jar"
if [ ! -f "$DBJAR" ]; then
  echo "Downloading MariaDB JDBC driver..."
  curl -fL "https://repo1.maven.org/maven2/org/mariadb/jdbc/mariadb-java-client/3.5.3/mariadb-java-client-3.5.3.jar" -o "$DBJAR"
fi
if [ ! -f "$BCRYPT" ]; then
  echo "Downloading BCrypt password hashing library..."
  curl -fL "https://repo1.maven.org/maven2/org/mindrot/jbcrypt/0.4/jbcrypt-0.4.jar" -o "$BCRYPT"
fi
echo "Compiling Attendly..."
javac -d out src/AttendanceAnalytics.java
echo "Launching Attendly against smart_attendance..."
java -cp "out:$DBJAR:$BCRYPT" AttendanceAnalytics
