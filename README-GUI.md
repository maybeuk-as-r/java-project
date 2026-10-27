# Smart Attendance Analytics GUI (MySQL/MariaDB)

This Swing GUI connects to the existing `smart_attendance` database used by the WT PHP project. It does not create, drop, or alter tables.

## Requirements
- Java 21+
- XAMPP MySQL/MariaDB running
- The existing database `smart_attendance`
- Internet access on first run to download MariaDB Connector/J

## Run on macOS
```bash
cd ~/Downloads/attendance-analytics-mysql
./run-gui.sh
```

Defaults match a typical local XAMPP setup:
- JDBC URL: `jdbc:mariadb://127.0.0.1:3306/smart_attendance`
- User: `root`
- Password: empty

If your local DB password differs, set it only in your terminal environment before launching:
```bash
export WT_DB_USER=root
read -s "WT_DB_PASSWORD?Database password: "; export WT_DB_PASSWORD; echo
./run-gui.sh
```

`WT_RECORDED_BY` must be the ID of an existing user in the database. It defaults to `2` (the demo teacher ID); set it to the appropriate teacher/admin ID for your local data, e.g. `export WT_RECORDED_BY=5`.

## Features
- Live dashboard counts from the database
- Class selector and enrolled student list
- Mark/save attendance for a date using present, absent, late, or excused
- Upsert behavior matches the PHP app's unique key on class/student/date
- Reports and a bar chart based on existing attendance records
- Present and Late count as attended, matching the PHP reports

The app does not create/edit user accounts or classes. Use the PHP website for those actions, so password hashing and role rules remain handled by the website.
