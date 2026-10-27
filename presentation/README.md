# Attendly Complete Project Bundle

This ZIP includes the Java Swing desktop application, setup/run script, presentation, and diagram files.

## Contents
- `smart-attendance-java/` — Java Swing GUI source, run script, README, and `.gitignore`.
- `presentation/Attendly_Attendance_Management_Presentation.pptx` — editable, self-explanatory presentation. It includes the class diagram and ER diagram slides.
- `presentation/diagrams/class-diagram.png` and `.mmd` — UML class diagram as image and Mermaid source.
- `presentation/diagrams/er-diagram.png` and `.mmd` — ER diagram as image and Mermaid source.
- `install-run-push.sh` — one script to replace the Java source in your existing clone, copy presentation/diagrams into the repository, launch the app, then commit and push after the GUI closes.

## Important database safety
The Java app connects to the existing `smart_attendance` database. This bundle does not import, drop, reset, or recreate database tables. Start XAMPP's MySQL/MariaDB before running. Default DB connection is `127.0.0.1:3306`, database `smart_attendance`, user `root`, empty password. If your root user has a password, export `WT_DB_PASSWORD='your-password'` in the same Terminal before starting the script. Do not commit credentials.

## Run manually
```bash
cd smart-attendance-java
./run-gui.sh
```
