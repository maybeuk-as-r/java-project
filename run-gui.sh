#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

# MariaDB Connector/J is compatible with the MariaDB bundled in XAMPP.
mkdir -p lib out
JAR="lib/mariadb-java-client-3.5.3.jar"
if [ ! -f "$JAR" ]; then
  echo "Downloading MariaDB JDBC driver..."
  curl -fL "https://repo1.maven.org/maven2/org/mariadb/jdbc/mariadb-java-client/3.5.3/mariadb-java-client-3.5.3.jar" -o "$JAR"
fi

echo "Compiling Java GUI..."
javac -cp "$JAR" -d out src/AttendanceAnalytics.java
echo "Launching Attendance Analytics..."
java -cp "out:$JAR" AttendanceAnalytics
