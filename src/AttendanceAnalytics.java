import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Terminal attendance manager with CSV storage and console charts.
 */
public class AttendanceAnalytics {
    private static final Scanner INPUT = new Scanner(System.in);
    private static final Path DATA_FILE = Path.of("data", "attendance.csv");
    private static final List<Student> students = new ArrayList<>();
    private static final List<AttendanceRecord> records = new ArrayList<>();

    public static void main(String[] args) {
        loadData();
        seedData();
        System.out.println("\n===============================================");
        System.out.println("        ATTENDANCE ANALYTICS CONSOLE");
        System.out.println("===============================================");
        System.out.println("Manage students, attendance, reports and charts.");

        boolean running = true;
        while (running) {
            showMenu();
            switch (readInt("Choose an option: ")) {
                case 1 -> listStudents();
                case 2 -> addStudent();
                case 3 -> editStudent();
                case 4 -> deleteStudent();
                case 5 -> markAttendance();
                case 6 -> showAttendanceRegister();
                case 7 -> showStudentReport();
                case 8 -> showClassDashboard();
                case 9 -> saveData();
                case 0 -> { saveData(); running = false; }
                default -> System.out.println("Please choose a valid menu option.");
            }
        }
        System.out.println("Saved. Goodbye!");
    }

    // Display main actions.
    private static void showMenu() {
        System.out.println("\n1. List students       2. Add student       3. Edit student");
        System.out.println("4. Delete student      5. Mark attendance   6. Attendance register");
        System.out.println("7. Student report      8. Class dashboard   9. Save data");
        System.out.println("0. Exit");
    }

    // List current students.
    private static void listStudents() {
        if (students.isEmpty()) { System.out.println("No students found."); return; }
        System.out.printf("\n%-8s %-24s %-16s%n", "ID", "NAME", "CLASS");
        System.out.println("--------------------------------------------------");
        students.stream().sorted(Comparator.comparing(Student::id)).forEach(s ->
            System.out.printf("%-8s %-24s %-16s%n", s.id(), s.name(), s.className()));
    }

    // Create a student.
    private static void addStudent() {
        String id = readText("Student ID: ");
        if (findStudent(id) != null) { System.out.println("That ID already exists."); return; }
        students.add(new Student(id, readText("Name: "), readText("Class: ")));
        System.out.println("Student added.");
    }

    // Update student details.
    private static void editStudent() {
        Student student = requireStudent();
        if (student == null) return;
        String name = readText("New name (Enter to keep " + student.name() + "): ", true);
        String className = readText("New class (Enter to keep " + student.className() + "): ", true);
        students.set(students.indexOf(student), new Student(student.id(),
            name.isBlank() ? student.name() : name, className.isBlank() ? student.className() : className));
        System.out.println("Student updated.");
    }

    // Remove a student and their attendance.
    private static void deleteStudent() {
        Student student = requireStudent();
        if (student == null) return;
        if (!readText("Type YES to delete " + student.name() + ": ").equalsIgnoreCase("YES")) return;
        students.remove(student);
        records.removeIf(r -> r.studentId().equals(student.id()));
        System.out.println("Student and related attendance removed.");
    }

    // Save present or absent status.
    private static void markAttendance() {
        Student student = requireStudent();
        if (student == null) return;
        String date = readText("Date (YYYY-MM-DD, Enter for today): ", true);
        if (date.isBlank()) date = LocalDate.now().toString();
        try { LocalDate.parse(date); } catch (Exception e) { System.out.println("Use YYYY-MM-DD."); return; }
        String status = readText("Status (P = Present, A = Absent): ").toUpperCase();
        if (!status.equals("P") && !status.equals("A")) { System.out.println("Enter P or A."); return; }
        records.removeIf(r -> r.studentId().equals(student.id()) && r.date().equals(date));
        records.add(new AttendanceRecord(student.id(), date, status));
        System.out.println("Attendance saved for " + student.name() + ".");
    }

    // Show records by date.
    private static void showAttendanceRegister() {
        String date = readText("Date (YYYY-MM-DD, Enter for all): ", true);
        System.out.printf("\n%-12s %-8s %-24s %-10s%n", "DATE", "ID", "NAME", "STATUS");
        System.out.println("------------------------------------------------------------");
        records.stream().filter(r -> date.isBlank() || r.date().equals(date)).sorted(Comparator.comparing(AttendanceRecord::date))
            .forEach(r -> { Student s = findStudent(r.studentId()); if (s != null)
                System.out.printf("%-12s %-8s %-24s %-10s%n", r.date(), s.id(), s.name(), r.status().equals("P") ? "Present" : "Absent"); });
    }

    // Show one student's attendance result.
    private static void showStudentReport() {
        Student student = requireStudent();
        if (student == null) return;
        List<AttendanceRecord> list = records.stream().filter(r -> r.studentId().equals(student.id())).toList();
        long present = list.stream().filter(r -> r.status().equals("P")).count();
        double percent = percentage(present, list.size());
        System.out.printf("\n%s (%s) — %d/%d present — %.1f%%%n", student.name(), student.className(), present, list.size(), percent);
        bar("Attendance", percent);
        System.out.println(percent >= 75 ? "Status: Good standing" : "Status: Attendance needs attention");
    }

    // Display class statistics and text charts.
    private static void showClassDashboard() {
        System.out.println("\n========== CLASS DASHBOARD ==========");
        long total = records.size();
        long present = records.stream().filter(r -> r.status().equals("P")).count();
        System.out.printf("Students: %d | Records: %d | Overall attendance: %.1f%%%n", students.size(), total, percentage(present, total));
        System.out.println("\nAttendance by student");
        students.stream().sorted(Comparator.comparing(Student::name)).forEach(s -> {
            List<AttendanceRecord> list = records.stream().filter(r -> r.studentId().equals(s.id())).toList();
            double percent = percentage(list.stream().filter(r -> r.status().equals("P")).count(), list.size());
            System.out.printf("%-20s ", clip(s.name(), 20)); barInline(percent); System.out.printf(" %5.1f%%%n", percent);
        });
        System.out.println("\nPresent vs absent");
        bar("Present", percentage(present, total));
        bar("Absent", percentage(total - present, total));
    }

    // Load stored CSV data.
    private static void loadData() {
        if (!Files.exists(DATA_FILE)) return;
        try {
            for (String line : Files.readAllLines(DATA_FILE)) {
                String[] p = line.split(",", -1);
                if (p[0].equals("S") && p.length == 4) students.add(new Student(p[1], p[2], p[3]));
                if (p[0].equals("A") && p.length == 4) records.add(new AttendanceRecord(p[1], p[2], p[3]));
            }
        } catch (IOException e) { System.out.println("Could not read saved data: " + e.getMessage()); }
    }

    // Save data as portable CSV.
    private static void saveData() {
        try {
            Files.createDirectories(DATA_FILE.getParent());
            List<String> output = new ArrayList<>();
            students.forEach(s -> output.add("S," + s.id() + "," + s.name() + "," + s.className()));
            records.forEach(r -> output.add("A," + r.studentId() + "," + r.date() + "," + r.status()));
            Files.write(DATA_FILE, output);
            System.out.println("Data saved to " + DATA_FILE + ".");
        } catch (IOException e) { System.out.println("Could not save data: " + e.getMessage()); }
    }

    // Add a first-run demo dataset.
    private static void seedData() {
        if (!students.isEmpty()) return;
        students.addAll(List.of(new Student("S001", "Aarav Sharma", "BCA-3"), new Student("S002", "Diya Patel", "BCA-3"), new Student("S003", "Ishaan Khan", "BCA-3"), new Student("S004", "Meera Singh", "BCA-3")));
        records.addAll(List.of(new AttendanceRecord("S001", "2026-10-20", "P"), new AttendanceRecord("S002", "2026-10-20", "P"), new AttendanceRecord("S003", "2026-10-20", "A"), new AttendanceRecord("S004", "2026-10-20", "P"), new AttendanceRecord("S001", "2026-10-21", "P"), new AttendanceRecord("S002", "2026-10-21", "A"), new AttendanceRecord("S003", "2026-10-21", "P"), new AttendanceRecord("S004", "2026-10-21", "P")));
    }

    private static Student requireStudent() { Student s = findStudent(readText("Student ID: ")); if (s == null) System.out.println("Student not found."); return s; }
    private static Student findStudent(String id) { return students.stream().filter(s -> s.id().equalsIgnoreCase(id)).findFirst().orElse(null); }
    private static double percentage(long part, long whole) { return whole == 0 ? 0 : part * 100.0 / whole; }
    private static void bar(String label, double value) { System.out.printf("%-12s ", label); barInline(value); System.out.printf(" %5.1f%%%n", value); }
    private static void barInline(double value) { int filled = (int) Math.round(value / 5); System.out.print("[" + "#".repeat(filled) + ".".repeat(20 - filled) + "]"); }
    private static String clip(String text, int length) { return text.length() <= length ? text : text.substring(0, length - 1) + "…"; }
    private static int readInt(String prompt) { try { return Integer.parseInt(readText(prompt)); } catch (NumberFormatException e) { return -1; } }
    private static String readText(String prompt) { return readText(prompt, false); }
    private static String readText(String prompt, boolean allowBlank) { System.out.print(prompt); String value = INPUT.nextLine().trim(); return value; }

    record Student(String id, String name, String className) { }
    record AttendanceRecord(String studentId, String date, String status) { }
}
