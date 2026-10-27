# Attendly — Smart Attendance Java GUI

Attendly is a refreshed desktop experience for the existing WT `smart_attendance` MariaDB database, with a dark navigation sidebar, cleaner cards, readable tables, and a more spacious dashboard. It does not create, drop, or alter database tables.

## Features
- Live dashboard counts
- User/student list with edit, activate/deactivate, and delete actions
- Class and enrolment overview, plus enrolment action
- Attendance class/date selector, mark/edit status, save/upsert, delete, and history
- Reports and attendance-rate bar chart
- Preserves statuses `present`, `absent`, `late`, `excused`; Present and Late count as attended

## Run on macOS
1. Ensure XAMPP MySQL is running.
2. In Terminal, run `./run-gui.sh` from this directory.
3. The first run downloads MariaDB Connector/J.

Defaults: `jdbc:mariadb://127.0.0.1:3306/smart_attendance`, user `root`, empty password.
If your database has a password, set `WT_DB_PASSWORD` in the environment before running.
`WT_RECORDED_BY` defaults to `2`; it must be an existing users.id because `attendance.recorded_by` has a foreign key.

New accounts are hashed with BCrypt via jBCrypt, compatible with PHP `password_verify()`. Java can create, edit, activate/deactivate, and delete users, subject to database foreign-key constraints.
