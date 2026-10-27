import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

/**
 * Attendance Analytics GUI: student management, attendance tracking and charts.
 * Requires JDK 17+. Uses only Java standard library.
 */
public class AttendanceAnalytics {
    private static final Path DATA = Path.of("data", "attendance.csv");
    private final List<Student> students = new ArrayList<>();
    private final List<Attendance> attendance = new ArrayList<>();
    private JFrame frame;
    private CardLayout cards;
    private JPanel content;
    private JLabel totalStudents, totalRecords, overallRate;
    private DefaultTableModel studentModel, attendanceModel;
    private JTable studentTable, attendanceTable;
    private ChartPanel chartPanel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AttendanceAnalytics().start());
    }

    private void start() {
        load();
        if (students.isEmpty()) seedDemo();
        frame = new JFrame("Attendly | Attendance Analytics");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1000, 680));
        frame.setSize(1180, 760);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());
        frame.add(sidebar(), BorderLayout.WEST);
        cards = new CardLayout();
        content = new JPanel(cards);
        content.setBackground(new Color(245, 247, 251));
        content.add(dashboardPage(), "Dashboard");
        content.add(studentsPage(), "Students");
        content.add(attendancePage(), "Attendance");
        content.add(reportsPage(), "Reports");
        frame.add(content, BorderLayout.CENTER);
        refreshAll();
        frame.setVisible(true);
    }

    private JPanel sidebar() {
        JPanel side = new JPanel();
        side.setPreferredSize(new Dimension(220, 0));
        side.setBackground(new Color(20, 31, 56));
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        JLabel brand = new JLabel("<html><b>  ATTENDLY</b><br><font size='3' color='#AAB8D6'>  Attendance Analytics</font></html>");
        brand.setForeground(Color.WHITE); brand.setFont(new Font("SansSerif", Font.BOLD, 20));
        brand.setBorder(BorderFactory.createEmptyBorder(28, 8, 35, 8));
        side.add(brand);
        for (String page : List.of("Dashboard", "Students", "Attendance", "Reports")) {
            JButton b = new JButton(page);
            b.setMaximumSize(new Dimension(190, 46));
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setFocusPainted(false); b.setForeground(Color.WHITE);
            b.setBackground(new Color(34, 48, 78)); b.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 10));
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setFont(new Font("SansSerif", Font.BOLD, 14));
            b.addActionListener(e -> { cards.show(content, page); if (page.equals("Reports")) chartPanel.repaint(); });
            side.add(b); side.add(Box.createVerticalStrut(10));
        }
        side.add(Box.createVerticalGlue());
        JLabel foot = new JLabel("  JDK 17+  •  CSV storage");
        foot.setForeground(new Color(166, 180, 207));
        foot.setBorder(BorderFactory.createEmptyBorder(15, 8, 20, 8));
        side.add(foot);
        return side;
    }

    private JPanel page(String title, String subtitle) {
        JPanel p = new JPanel(new BorderLayout(0, 18));
        p.setBackground(new Color(245, 247, 251));
        p.setBorder(BorderFactory.createEmptyBorder(28, 30, 28, 30));
        JPanel heading = new JPanel();
        heading.setOpaque(false); heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title); t.setFont(new Font("SansSerif", Font.BOLD, 28)); t.setForeground(new Color(25, 39, 67));
        JLabel s = new JLabel(subtitle); s.setFont(new Font("SansSerif", Font.PLAIN, 14)); s.setForeground(new Color(105, 119, 145));
        heading.add(t); heading.add(Box.createVerticalStrut(5)); heading.add(s);
        p.add(heading, BorderLayout.NORTH);
        return p;
    }

    private JPanel dashboardPage() {
        JPanel p = page("Dashboard", "A quick overview of your class attendance.");
        JPanel body = new JPanel(new BorderLayout(0, 18)); body.setOpaque(false);
        JPanel stats = new JPanel(new GridLayout(1, 3, 15, 0)); stats.setOpaque(false);
        totalStudents = new JLabel("0"); totalRecords = new JLabel("0"); overallRate = new JLabel("0%");
        stats.add(statCard("TOTAL STUDENTS", totalStudents, new Color(57, 105, 230)));
        stats.add(statCard("ATTENDANCE RECORDS", totalRecords, new Color(18, 157, 126)));
        stats.add(statCard("OVERALL ATTENDANCE", overallRate, new Color(147, 90, 214)));
        body.add(stats, BorderLayout.NORTH);
        chartPanel = new ChartPanel();
        JPanel chartCard = whitePanel(new BorderLayout());
        chartCard.setBorder(BorderFactory.createEmptyBorder(18, 20, 20, 20));
        JLabel ct = new JLabel("Attendance by student"); ct.setFont(new Font("SansSerif", Font.BOLD, 17)); ct.setForeground(new Color(25,39,67));
        chartCard.add(ct, BorderLayout.NORTH); chartCard.add(chartPanel, BorderLayout.CENTER);
        body.add(chartCard, BorderLayout.CENTER);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private JPanel statCard(String title, JLabel value, Color accent) {
        JPanel c = whitePanel(new BorderLayout(0, 15));
        c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));
        JLabel t = new JLabel(title); t.setFont(new Font("SansSerif", Font.BOLD, 11)); t.setForeground(new Color(112, 125, 150));
        value.setFont(new Font("SansSerif", Font.BOLD, 30)); value.setForeground(new Color(25,39,67));
        c.add(t, BorderLayout.NORTH); c.add(value, BorderLayout.CENTER);
        return c;
    }

    private JPanel studentsPage() {
        JPanel p = page("Students", "Add, update or remove students from your class.");
        studentModel = new DefaultTableModel(new Object[]{"Student ID", "Name", "Class"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        studentTable = styledTable(studentModel);
        p.add(tableCard(studentTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); actions.setOpaque(false);
        JButton add = button("＋ Add Student"), edit = button("Edit Selected"), del = button("Delete Selected");
        add.addActionListener(e -> addStudentDialog());
        edit.addActionListener(e -> editStudentDialog());
        del.addActionListener(e -> deleteStudent());
        actions.add(add); actions.add(edit); actions.add(del);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    private JPanel attendancePage() {
        JPanel p = page("Attendance", "Record attendance for a student on a specific date.");
        attendanceModel = new DefaultTableModel(new Object[]{"Date", "Student ID", "Student Name", "Status"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        attendanceTable = styledTable(attendanceModel);
        p.add(tableCard(attendanceTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); actions.setOpaque(false);
        JButton mark = button("＋ Mark Attendance"), remove = button("Delete Record");
        mark.addActionListener(e -> markAttendanceDialog());
        remove.addActionListener(e -> deleteAttendance());
        actions.add(mark); actions.add(remove); p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    private JPanel reportsPage() {
        JPanel p = page("Reports & Insights", "Visual summary of present and absent attendance.");
        JPanel body = new JPanel(new GridLayout(2, 1, 0, 16)); body.setOpaque(false);
        JPanel byStudent = whitePanel(new BorderLayout()); byStudent.setBorder(BorderFactory.createEmptyBorder(18,20,18,20));
        JLabel title = new JLabel("Student attendance percentage"); title.setFont(new Font("SansSerif", Font.BOLD, 17));
        byStudent.add(title, BorderLayout.NORTH); byStudent.add(new ChartPanel(), BorderLayout.CENTER);
        JPanel totals = whitePanel(new BorderLayout()); totals.setBorder(BorderFactory.createEmptyBorder(18,20,18,20));
        JLabel title2 = new JLabel("Present vs absent"); title2.setFont(new Font("SansSerif", Font.BOLD, 17));
        totals.add(title2, BorderLayout.NORTH); totals.add(new TotalsChart(), BorderLayout.CENTER);
        body.add(byStudent); body.add(totals); p.add(body, BorderLayout.CENTER);
        return p;
    }

    private JPanel tableCard(JTable table) {
        JPanel c = whitePanel(new BorderLayout());
        c.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JScrollPane sp = new JScrollPane(table); sp.setBorder(BorderFactory.createEmptyBorder());
        c.add(sp, BorderLayout.CENTER); return c;
    }

    private JTable styledTable(DefaultTableModel model) {
        JTable t = new JTable(model);
        t.setRowHeight(34); t.setFont(new Font("SansSerif", Font.PLAIN, 13));
        t.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        t.getTableHeader().setBackground(new Color(236, 241, 249));
        t.setSelectionBackground(new Color(220, 231, 255));
        t.setGridColor(new Color(232, 236, 243)); t.setFillsViewportHeight(true);
        return t;
    }

    private JPanel whitePanel(LayoutManager layout) {
        JPanel p = new JPanel(layout); p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createLineBorder(new Color(230, 234, 242)));
        return p;
    }

    private JButton button(String text) {
        JButton b = new JButton(text); b.setFocusPainted(false);
        b.setFont(new Font("SansSerif", Font.BOLD, 13)); b.setForeground(Color.WHITE);
        b.setBackground(new Color(48, 91, 205)); b.setBorder(BorderFactory.createEmptyBorder(11, 16, 11, 16));
        return b;
    }

    private void addStudentDialog() {
        JTextField id = new JTextField(), name = new JTextField(), cls = new JTextField("BCA-3");
        Object[] fields = {"Student ID", id, "Full name", name, "Class / Section", cls};
        if (JOptionPane.showConfirmDialog(frame, fields, "Add student", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        if (id.getText().isBlank() || name.getText().isBlank() || cls.getText().isBlank()) { error("Please fill in every field."); return; }
        if (findStudent(id.getText().trim()) != null) { error("That student ID already exists."); return; }
        students.add(new Student(id.getText().trim(), name.getText().trim(), cls.getText().trim()));
        persist(); refreshAll();
    }

    private void editStudentDialog() {
        int row = studentTable.getSelectedRow(); if (row < 0) { error("Select a student first."); return; }
        Student s = findStudent(studentModel.getValueAt(row, 0).toString());
        JTextField name = new JTextField(s.name), cls = new JTextField(s.className);
        Object[] fields = {"Full name", name, "Class / Section", cls};
        if (JOptionPane.showConfirmDialog(frame, fields, "Edit student", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        if (name.getText().isBlank() || cls.getText().isBlank()) { error("Fields cannot be empty."); return; }
        s.name = name.getText().trim(); s.className = cls.getText().trim(); persist(); refreshAll();
    }

    private void deleteStudent() {
        int row = studentTable.getSelectedRow(); if (row < 0) { error("Select a student first."); return; }
        String id = studentModel.getValueAt(row, 0).toString();
        if (JOptionPane.showConfirmDialog(frame, "Delete this student and their attendance records?", "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        students.removeIf(s -> s.id.equals(id)); attendance.removeIf(a -> a.studentId.equals(id)); persist(); refreshAll();
    }

    private void markAttendanceDialog() {
        if (students.isEmpty()) { error("Add a student first."); return; }
        JComboBox<String> student = new JComboBox<>(students.stream().map(s -> s.id + " | " + s.name).toArray(String[]::new));
        JTextField date = new JTextField(LocalDate.now().toString());
        JComboBox<String> status = new JComboBox<>(new String[]{"Present", "Absent"});
        Object[] fields = {"Student", student, "Date (YYYY-MM-DD)", date, "Status", status};
        if (JOptionPane.showConfirmDialog(frame, fields, "Mark attendance", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try { LocalDate.parse(date.getText().trim()); } catch (Exception ex) { error("Use a valid date in YYYY-MM-DD format."); return; }
        String id = student.getSelectedItem().toString().split(" \\| ")[0];
        String st = status.getSelectedItem().equals("Present") ? "P" : "A";
        attendance.removeIf(a -> a.studentId.equals(id) && a.date.equals(date.getText().trim()));
        attendance.add(new Attendance(id, date.getText().trim(), st)); persist(); refreshAll();
    }

    private void deleteAttendance() {
        int row = attendanceTable.getSelectedRow(); if (row < 0) { error("Select an attendance record first."); return; }
        String date = attendanceModel.getValueAt(row,0).toString(), id = attendanceModel.getValueAt(row,1).toString();
        attendance.removeIf(a -> a.date.equals(date) && a.studentId.equals(id)); persist(); refreshAll();
    }

    private void refreshAll() {
        if (studentModel != null) {
            studentModel.setRowCount(0);
            students.stream().sorted(Comparator.comparing(s -> s.id)).forEach(s -> studentModel.addRow(new Object[]{s.id,s.name,s.className}));
        }
        if (attendanceModel != null) {
            attendanceModel.setRowCount(0);
            attendance.stream().sorted(Comparator.comparing((Attendance a) -> a.date).reversed()).forEach(a -> {
                Student s = findStudent(a.studentId);
                if (s != null) attendanceModel.addRow(new Object[]{a.date,a.studentId,s.name,a.status.equals("P") ? "Present" : "Absent"});
            });
        }
        if (totalStudents != null) totalStudents.setText(String.valueOf(students.size()));
        if (totalRecords != null) totalRecords.setText(String.valueOf(attendance.size()));
        long present = attendance.stream().filter(a -> a.status.equals("P")).count();
        if (overallRate != null) overallRate.setText(String.format(Locale.US, "%.1f%%", pct(present, attendance.size())));
        if (chartPanel != null) chartPanel.repaint();
        frame.repaint();
    }

    private void error(String message) { JOptionPane.showMessageDialog(frame, message, "Attendly", JOptionPane.WARNING_MESSAGE); }
    private Student findStudent(String id) { return students.stream().filter(s -> s.id.equalsIgnoreCase(id)).findFirst().orElse(null); }
    private double pct(long a, long b) { return b == 0 ? 0 : a * 100.0 / b; }

    private void seedDemo() {
        students.addAll(List.of(new Student("S001","Aarav Sharma","BCA-3"), new Student("S002","Diya Patel","BCA-3"),
                new Student("S003","Ishaan Khan","BCA-3"), new Student("S004","Meera Singh","BCA-3")));
        attendance.addAll(List.of(new Attendance("S001","2026-10-20","P"),new Attendance("S002","2026-10-20","P"),
                new Attendance("S003","2026-10-20","A"),new Attendance("S004","2026-10-20","P"),
                new Attendance("S001","2026-10-21","P"),new Attendance("S002","2026-10-21","A"),
                new Attendance("S003","2026-10-21","P"),new Attendance("S004","2026-10-21","P")));
        persist();
    }

    private void load() {
        if (!Files.exists(DATA)) return;
        try {
            for (String line : Files.readAllLines(DATA)) {
                String[] p = line.split(",", -1);
                if (p.length == 4 && p[0].equals("S")) students.add(new Student(p[1],p[2],p[3]));
                else if (p.length == 4 && p[0].equals("A")) attendance.add(new Attendance(p[1],p[2],p[3]));
            }
        } catch (IOException e) { JOptionPane.showMessageDialog(null, "Could not load data: " + e.getMessage()); }
    }

    private void persist() {
        try {
            Files.createDirectories(DATA.getParent());
            List<String> lines = new ArrayList<>();
            students.forEach(s -> lines.add("S,"+clean(s.id)+","+clean(s.name)+","+clean(s.className)));
            attendance.forEach(a -> lines.add("A,"+clean(a.studentId)+","+a.date+","+a.status));
            Files.write(DATA, lines);
        } catch (IOException e) { error("Could not save data: " + e.getMessage()); }
    }
    private String clean(String s) { return s.replace(",", " "); }

    private class ChartPanel extends JPanel {
        ChartPanel() { setBackground(Color.WHITE); setPreferredSize(new Dimension(500, 310)); }
        protected void paintComponent(Graphics g) {
            super.paintComponent(g); Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int left=145, right=45, top=24, rowH= Math.max(34, (getHeight()-top-20)/Math.max(1,students.size()));
            int maxW=Math.max(80,getWidth()-left-right);
            g2.setFont(new Font("SansSerif",Font.PLAIN,12));
            for(int i=0;i<students.size();i++){
                Student s=students.get(i);
                List<Attendance> list=attendance.stream().filter(a->a.studentId.equals(s.id)).toList();
                long p=list.stream().filter(a->a.status.equals("P")).count();
                double v=pct(p,list.size()); int y=top+i*rowH;
                g2.setColor(new Color(64,78,105)); g2.drawString(s.name.length()>18?s.name.substring(0,17)+"…":s.name,10,y+17);
                g2.setColor(new Color(237,241,248)); g2.fillRoundRect(left,y,maxW,18,9,9);
                g2.setColor(new Color(58,111,230)); g2.fillRoundRect(left,y,(int)(maxW*v/100),18,9,9);
                g2.setColor(new Color(66,80,105)); g2.drawString(String.format(Locale.US,"%.0f%%",v),left+maxW+8,y+14);
            }
            if(students.isEmpty()){g2.setColor(Color.GRAY);g2.drawString("Add students to see the chart.",20,40);}
            g2.dispose();
        }
    }

    private class TotalsChart extends JPanel {
        TotalsChart(){setBackground(Color.WHITE);setPreferredSize(new Dimension(400,170));}
        protected void paintComponent(Graphics g){
            super.paintComponent(g); Graphics2D g2=(Graphics2D)g.create();
            long p=attendance.stream().filter(a->a.status.equals("P")).count(), a=attendance.size()-p;
            int total=Math.max(1,attendance.size()), x=25, y=35, w=Math.max(150,getWidth()-190);
            g2.setColor(new Color(18,157,126));g2.fillRoundRect(x,y,(int)(w*p/total),28,8,8);
            g2.setColor(new Color(236,105,105));g2.fillRoundRect(x+(int)(w*p/total),y,(int)(w*a/total),28,8,8);
            g2.setFont(new Font("SansSerif",Font.BOLD,13));g2.setColor(new Color(35,50,75));
            g2.drawString("Present: "+p, x, y+60);g2.drawString("Absent: "+a, x+125, y+60);
            g2.drawString(String.format(Locale.US,"Present %.1f%%   •   Absent %.1f%%",pct(p,total),pct(a,total)),x,y+88);
            g2.dispose();
        }
    }

    private static class Student {
        String id,name,className;
        Student(String id,String name,String className){this.id=id;this.name=name;this.className=className;}
    }
    private static class Attendance {
        String studentId,date,status;
        Attendance(String studentId,String date,String status){this.studentId=studentId;this.date=date;this.status=status;}
    }
}
