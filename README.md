# Attendance Analytics Console

A Java terminal application that extends the Attendance System project. It provides student and attendance CRUD actions, CSV persistence, individual reports, and readable text-based graphs.

## Features

- Add, list, edit, and delete students
- Mark present/absent attendance by date
- View the full attendance register
- Generate individual attendance reports
- View a class dashboard with bar graphs
- Save data automatically as `data/attendance.csv`
- Start with safe demo data on the first run

## Requirements

- Java Development Kit (JDK) 17 or later
- A terminal / Command Prompt

## Run

From the project folder:

```bash
javac -d out src/AttendanceAnalytics.java
java -cp out AttendanceAnalytics
```

## Menu overview

| Option | Action |
| --- | --- |
| 1–4 | Student CRUD operations |
| 5 | Create or update attendance for a student/date |
| 6 | Read attendance records |
| 7 | View an individual report |
| 8 | View class totals and bar charts |
| 9 / 0 | Save data / save and exit |

## Project layout

```text
java-project/
├── src/AttendanceAnalytics.java
├── data/                    # Created after first save
└── README.md
```

## Notes

This is a dependency-free terminal project, so it can be compiled with the standard JDK. CSV values should not contain commas.
