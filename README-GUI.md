# Attendly — Attendance Analytics GUI

A Java Swing desktop application for managing students, recording attendance, and viewing visual attendance charts.

## Features
- Modern desktop GUI with Dashboard, Students, Attendance, and Reports pages
- Add, edit, and delete students
- Mark Present/Absent attendance by date; one record per student per date
- Student attendance percentage bar chart
- Present vs absent summary chart
- Automatic CSV persistence in `data/attendance.csv`
- First-run demo records so the interface is populated immediately
- No external libraries required

## Requirements
- JDK 17 or later

## Run
**macOS / Linux**
```bash
javac -d out src/AttendanceAnalytics.java
java -cp out AttendanceAnalytics
```

**Windows**
```bat
javac -d out src\AttendanceAnalytics.java
java -cp out AttendanceAnalytics
```

Run the commands from the project folder. The `data` folder is created automatically.

## Project structure
```text
attendance-analytics-gui/
├── src/
│   └── AttendanceAnalytics.java
├── .gitignore
├── README.md
└── run.sh
```

## Data format
The application stores students and attendance records as CSV rows:
- `S,studentId,name,className`
- `A,studentId,YYYY-MM-DD,P|A`

Names and class values containing commas are normalized to spaces for simple CSV storage.

## Academic project summary
**Problem:** Attendance registers are slow to update and make class-level patterns difficult to see.
**Solution:** A GUI that keeps student records and daily attendance in one place, then visualizes attendance percentages.
**Core Java concepts:** Classes and objects, collections, file handling, exception handling, Swing GUI components, event listeners, and custom Java2D chart drawing.
