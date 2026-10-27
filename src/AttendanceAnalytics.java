import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

/**
 * Smart Attendance Java GUI for the existing WT smart_attendance database.
 * Uses existing tables only. Does not create, drop, or migrate schema.
 *
 * Environment variables:
 * WT_DB_URL       default jdbc:mariadb://127.0.0.1:3306/smart_attendance
 * WT_DB_USER      default root
 * WT_DB_PASSWORD  default empty
 * WT_RECORDED_BY  default 2; must be an existing user ID for attendance writes
 */
public class AttendanceAnalytics {
    static final String DB_URL = System.getenv().getOrDefault("WT_DB_URL",
            "jdbc:mariadb://127.0.0.1:3306/smart_attendance");
    static final String DB_USER = System.getenv().getOrDefault("WT_DB_USER", "root");
    static final String DB_PASSWORD = System.getenv().getOrDefault("WT_DB_PASSWORD", "");
    static final int RECORDED_BY = Integer.parseInt(System.getenv().getOrDefault("WT_RECORDED_BY", "2"));
    static final Color BLUE = new Color(35, 91, 180);
    static final String[] STATUSES = {"present", "absent", "late", "excused"};

    JFrame frame;
    JLabel overview = new JLabel("Connecting...");
    DefaultTableModel usersModel, classesModel, attendanceModel, reportsModel;
    JTable usersTable, classesTable, attendanceTable, reportsTable;
    JComboBox<Item> attendanceClass = new JComboBox<>();
    JTextField attendanceDate = new JTextField(LocalDate.now().toString(), 10);
    BarChart chart = new BarChart();

    static Connection db() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AttendanceAnalytics().launch());
    }

    void launch() {
        try (Connection c = db()) {
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT 1")) {
                if (!r.next()) throw new SQLException("Database ping failed.");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "Could not connect to smart_attendance.\nStart XAMPP MySQL and check WT_DB_URL / WT_DB_USER / WT_DB_PASSWORD.\n\n"
                            + e.getMessage(), "Database connection failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        frame = new JFrame("Smart Attendance | Java Admin");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 760);
        frame.setMinimumSize(new Dimension(980, 620));
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        root.setBackground(new Color(244, 247, 252));
        frame.setContentPane(root);

        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.setOpaque(false);
        JLabel title = new JLabel("Smart Attendance Management");
        title.setFont(new Font("SansSerif", Font.BOLD, 25));
        overview.setFont(new Font("SansSerif", Font.PLAIN, 13));
        header.add(title, BorderLayout.NORTH);
        header.add(overview, BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Dashboard", dashboard());
        tabs.addTab("Users / Students CRUD", usersPanel());
        tabs.addTab("Classes & Enrolments", classesPanel());
        tabs.addTab("Attendance CRUD", attendancePanel());
        tabs.addTab("Reports & Charts", reportsPanel());
        root.add(tabs, BorderLayout.CENTER);

        frame.setVisible(true);
        refreshAll();
    }

    JPanel dashboard() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        JLabel explainer = new JLabel("<html><h2>Live database dashboard</h2>"
                + "Connected to the same <b>smart_attendance</b> database as your WT PHP website.<br>"
                + "Changes made here are stored in MySQL and will be visible to the PHP app too.</html>");
        p.add(explainer, BorderLayout.NORTH);
        p.add(chart, BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh all data");
        refresh.addActionListener(e -> refreshAll());
        p.add(refresh, BorderLayout.SOUTH);
        return p;
    }

    JPanel usersPanel() {
        usersModel = model("ID", "Full name", "Email", "Role", "Active");
        usersTable = new JTable(usersModel);
        usersTable.setRowHeight(25);
        JPanel p = standardTablePanel(usersTable);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add user/student (use PHP)");
        JButton edit = new JButton("Edit selected");
        JButton toggle = new JButton("Toggle active");
        JButton delete = new JButton("Delete selected");
        JButton reload = new JButton("Refresh");
        add.addActionListener(e -> addUser());
        edit.addActionListener(e -> editUser());
        toggle.addActionListener(e -> toggleUser());
        delete.addActionListener(e -> deleteUser());
        reload.addActionListener(e -> loadUsers());
        actions.add(add); actions.add(edit); actions.add(toggle); actions.add(delete); actions.add(reload);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    JPanel classesPanel() {
        classesModel = model("ID", "Code", "Class name", "Teacher", "Enrolled students");
        classesTable = new JTable(classesModel);
        classesTable.setRowHeight(25);
        JPanel p = standardTablePanel(classesTable);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton enroll = new JButton("Enroll student");
        JButton refresh = new JButton("Refresh");
        enroll.addActionListener(e -> enrollStudent());
        refresh.addActionListener(e -> loadClasses());
        actions.add(enroll); actions.add(refresh);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    JPanel attendancePanel() {
        attendanceModel = model("Record ID", "Student ID", "Student", "Class", "Date", "Status");
        attendanceTable = new JTable(attendanceModel);
        attendanceTable.setRowHeight(25);
        JPanel p = standardTablePanel(attendanceTable);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("Class:"));
        controls.add(attendanceClass);
        controls.add(new JLabel("Date YYYY-MM-DD:"));
        controls.add(attendanceDate);
        JButton load = new JButton("Load enrolled students");
        load.addActionListener(e -> loadAttendance());
        controls.add(load);
        p.add(controls, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton mark = new JButton("Mark / edit selected status");
        JButton saveAll = new JButton("Save class attendance");
        JButton delete = new JButton("Delete selected record");
        JButton refresh = new JButton("Refresh history");
        mark.addActionListener(e -> editAttendanceStatus());
        saveAll.addActionListener(e -> saveClassAttendance());
        delete.addActionListener(e -> deleteAttendance());
        refresh.addActionListener(e -> loadAttendanceHistory());
        actions.add(mark); actions.add(saveAll); actions.add(delete); actions.add(refresh);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    JPanel reportsPanel() {
        reportsModel = model("Student", "Class", "Sessions", "Attended", "Attendance rate");
        reportsTable = new JTable(reportsModel);
        reportsTable.setRowHeight(25);
        JPanel p = standardTablePanel(reportsTable);
        JButton refresh = new JButton("Refresh reports and chart");
        refresh.addActionListener(e -> loadReports());
        p.add(refresh, BorderLayout.SOUTH);
        return p;
    }

    JPanel standardTablePanel(JTable table) {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    DefaultTableModel model(String... cols) {
        return new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
    }

    void refreshAll() {
        try {
            loadUsers(); loadClasses(); loadAttendanceHistory(); loadReports(); loadOverview();
        } catch (Exception e) { error("Refresh failed", e); }
    }

    void loadOverview() throws SQLException {
        String q = "SELECT (SELECT COUNT(*) FROM users WHERE role='student' AND is_active=1) students,"
                + "(SELECT COUNT(*) FROM users WHERE role='teacher' AND is_active=1) teachers,"
                + "(SELECT COUNT(*) FROM classes) classes,"
                + "(SELECT COUNT(*) FROM enrollments) enrollments,"
                + "(SELECT COUNT(*) FROM attendance) records,"
                + "(SELECT COUNT(*) FROM attendance WHERE attendance_date=CURDATE()) today";
        try (Connection c = db(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) {
            r.next();
            overview.setText("Students: " + r.getInt("students") + "  |  Teachers: " + r.getInt("teachers")
                    + "  |  Classes: " + r.getInt("classes") + "  |  Enrolments: " + r.getInt("enrollments")
                    + "  |  Attendance records: " + r.getInt("records") + "  |  Today's records: " + r.getInt("today"));
        }
    }

    void loadUsers() {
        if (usersModel == null) return;
        usersModel.setRowCount(0);
        String q = "SELECT id,full_name,email,role,is_active FROM users ORDER BY role,full_name";
        try (Connection c = db(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) {
            while (r.next()) usersModel.addRow(new Object[]{r.getInt("id"), r.getString("full_name"),
                    r.getString("email"), r.getString("role"), r.getInt("is_active") == 1 ? "Active" : "Disabled"});
        } catch (Exception e) { error("Could not load users", e); }
    }

    void addUser() {
        info("Create new accounts through the WT PHP Users page so passwords are stored with PHP password_hash(). "
                + "This Java app can edit, activate/deactivate, and delete existing accounts without risking incompatible password hashes.");
    }

    void editUser() {
        int row = selected(usersTable);
        if (row < 0) return;
        int id = Integer.parseInt(usersModel.getValueAt(row, 0).toString());
        JTextField name = new JTextField(usersModel.getValueAt(row, 1).toString());
        JTextField email = new JTextField(usersModel.getValueAt(row, 2).toString());
        JComboBox<String> role = new JComboBox<>(new String[]{"admin", "teacher", "student"});
        role.setSelectedItem(usersModel.getValueAt(row, 3).toString());
        JPanel form = formPanel(new String[]{"Full name", "Email", "Role"},
                new JComponent[]{name, email, role});
        if (JOptionPane.showConfirmDialog(frame, form, "Edit user " + id, JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try (Connection c = db(); PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET full_name=?,email=?,role=? WHERE id=?")) {
            ps.setString(1, name.getText().trim()); ps.setString(2, email.getText().trim());
            ps.setString(3, role.getSelectedItem().toString()); ps.setInt(4, id); ps.executeUpdate();
            loadUsers(); loadClasses(); loadOverview();
        } catch (Exception e) { error("Could not update user. Check unique email and foreign-key constraints.", e); }
    }

    void toggleUser() {
        int row = selected(usersTable); if (row < 0) return;
        int id = Integer.parseInt(usersModel.getValueAt(row, 0).toString());
        int active = usersModel.getValueAt(row, 4).toString().equals("Active") ? 0 : 1;
        try (Connection c = db(); PreparedStatement ps = c.prepareStatement("UPDATE users SET is_active=? WHERE id=?")) {
            ps.setInt(1, active); ps.setInt(2, id); ps.executeUpdate(); loadUsers(); loadOverview();
        } catch (Exception e) { error("Could not change account status", e); }
    }

    void deleteUser() {
        int row = selected(usersTable); if (row < 0) return;
        int id = Integer.parseInt(usersModel.getValueAt(row, 0).toString());
        if (JOptionPane.showConfirmDialog(frame, "Delete user ID " + id + "?\nThe database foreign keys may prevent deleting users referenced by classes or attendance.",
                "Confirm delete", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try (Connection c = db(); PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id=?")) {
            ps.setInt(1, id); ps.executeUpdate(); loadUsers(); loadClasses(); loadAttendanceHistory(); loadOverview();
        } catch (Exception e) { error("Could not delete user. Referenced users must be reassigned or retained.", e); }
    }

    void loadClasses() {
        if (classesModel == null) return;
        classesModel.setRowCount(0);
        String q = "SELECT c.id,c.class_code,c.class_name,u.full_name teacher_name,COUNT(e.student_id) students "
                + "FROM classes c JOIN users u ON u.id=c.teacher_id LEFT JOIN enrollments e ON e.class_id=c.id "
                + "GROUP BY c.id,c.class_code,c.class_name,u.full_name ORDER BY c.class_code";
        try (Connection c = db(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) {
            while (r.next()) classesModel.addRow(new Object[]{r.getInt("id"), r.getString("class_code"),
                    r.getString("class_name"), r.getString("teacher_name"), r.getInt("students")});
            reloadClassChoices(c);
        } catch (Exception e) { error("Could not load classes", e); }
    }

    void reloadClassChoices(Connection c) throws SQLException {
        Item previous = (Item) attendanceClass.getSelectedItem();
        attendanceClass.removeAllItems();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                "SELECT id,class_code,class_name FROM classes ORDER BY class_code")) {
            while (r.next()) attendanceClass.addItem(new Item(r.getInt(1), r.getString(2), r.getString(3)));
        }
        if (previous != null) for (int i=0; i<attendanceClass.getItemCount(); i++)
            if (attendanceClass.getItemAt(i).id == previous.id) attendanceClass.setSelectedIndex(i);
    }

    void enrollStudent() {
        List<Item> classes = new ArrayList<>(), students = new ArrayList<>();
        try (Connection c = db(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                "SELECT id,class_code,class_name FROM classes ORDER BY class_code")) {
            while (r.next()) classes.add(new Item(r.getInt(1), r.getString(2), r.getString(3)));
        } catch (Exception e) { error("Could not list classes", e); return; }
        try (Connection c = db(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                "SELECT id,full_name,'' FROM users WHERE role='student' AND is_active=1 ORDER BY full_name")) {
            while (r.next()) students.add(new Item(r.getInt(1), r.getString(2), ""));
        } catch (Exception e) { error("Could not list students", e); return; }
        if (classes.isEmpty() || students.isEmpty()) { info("You need at least one class and one active student."); return; }
        JComboBox<Item> cbClass = new JComboBox<>(classes.toArray(new Item[0]));
        JComboBox<Item> cbStudent = new JComboBox<>(students.toArray(new Item[0]));
        JPanel form = formPanel(new String[]{"Class", "Student"}, new JComponent[]{cbClass, cbStudent});
        if (JOptionPane.showConfirmDialog(frame, form, "Enroll student", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        Item cl = (Item) cbClass.getSelectedItem(), st = (Item) cbStudent.getSelectedItem();
        try (Connection c = db(); PreparedStatement ps = c.prepareStatement("INSERT INTO enrollments(class_id,student_id) VALUES(?,?)")) {
            ps.setInt(1, cl.id); ps.setInt(2, st.id); ps.executeUpdate(); loadClasses(); loadOverview();
        } catch (Exception e) { error("Could not enroll student (they may already be enrolled)", e); }
    }

    void loadAttendance() {
        Item cl = (Item) attendanceClass.getSelectedItem();
        if (cl == null) return;
        try { LocalDate.parse(attendanceDate.getText().trim()); }
        catch (Exception e) { info("Enter date as YYYY-MM-DD."); return; }
        attendanceModel.setRowCount(0);
        String q = "SELECT a.id record_id,u.id student_id,u.full_name,c.class_code,? att_date,"
                + "COALESCE(a.status,'present') status FROM enrollments e JOIN users u ON u.id=e.student_id "
                + "JOIN classes c ON c.id=e.class_id LEFT JOIN attendance a ON a.class_id=e.class_id "
                + "AND a.student_id=e.student_id AND a.attendance_date=? WHERE e.class_id=? ORDER BY u.full_name";
        try (Connection c = db(); PreparedStatement ps = c.prepareStatement(q)) {
            ps.setString(1, attendanceDate.getText().trim()); ps.setDate(2, java.sql.Date.valueOf(attendanceDate.getText().trim())); ps.setInt(3, cl.id);
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) attendanceModel.addRow(new Object[]{r.getObject("record_id"), r.getInt("student_id"),
                        r.getString("full_name"), r.getString("class_code"), r.getString("att_date"), r.getString("status")});
            }
        } catch (Exception e) { error("Could not load selected class attendance", e); }
    }

    void loadAttendanceHistory() {
        if (attendanceModel == null) return;
        attendanceModel.setRowCount(0);
        String q = "SELECT a.id record_id,a.student_id,u.full_name,c.class_code,a.attendance_date,a.status "
                + "FROM attendance a JOIN users u ON u.id=a.student_id JOIN classes c ON c.id=a.class_id "
                + "ORDER BY a.attendance_date DESC,u.full_name LIMIT 1000";
        try (Connection c = db(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) {
            while (r.next()) attendanceModel.addRow(new Object[]{r.getInt("record_id"), r.getInt("student_id"),
                    r.getString("full_name"), r.getString("class_code"), r.getDate("attendance_date").toString(), r.getString("status")});
        } catch (Exception e) { error("Could not load attendance history", e); }
    }

    void editAttendanceStatus() {
        int row = selected(attendanceTable); if (row < 0) return;
        String current = attendanceModel.getValueAt(row, 5).toString();
        JComboBox<String> status = new JComboBox<>(STATUSES); status.setSelectedItem(current);
        if (JOptionPane.showConfirmDialog(frame, status, "Set attendance status", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        attendanceModel.setValueAt(status.getSelectedItem().toString(), row, 5);
    }

    void saveClassAttendance() {
        Item cl = (Item) attendanceClass.getSelectedItem();
        if (cl == null) return;
        LocalDate date;
        try { date = LocalDate.parse(attendanceDate.getText().trim()); }
        catch (Exception e) { info("Enter date as YYYY-MM-DD."); return; }
        String sql = "INSERT INTO attendance(class_id,student_id,attendance_date,status,recorded_by) VALUES(?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE status=VALUES(status),recorded_by=VALUES(recorded_by)";
        try (Connection c = db()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i=0;i<attendanceModel.getRowCount();i++) {
                    int studentId = Integer.parseInt(attendanceModel.getValueAt(i,1).toString());
                    String rowClass = attendanceModel.getValueAt(i,3).toString();
                    String rowDate = attendanceModel.getValueAt(i,4).toString();
                    if (!rowClass.equals(cl.code) || !rowDate.equals(date.toString())) continue;
                    String status = attendanceModel.getValueAt(i,5).toString().toLowerCase(Locale.ROOT);
                    if (!Arrays.asList(STATUSES).contains(status)) throw new SQLException("Invalid status: " + status);
                    ps.setInt(1, cl.id); ps.setInt(2, studentId); ps.setDate(3, java.sql.Date.valueOf(date));
                    ps.setString(4,status); ps.setInt(5,RECORDED_BY); ps.addBatch();
                }
                ps.executeBatch(); c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
            loadAttendanceHistory(); loadReports(); loadOverview();
            info("Attendance saved to the shared smart_attendance database.");
        } catch (Exception e) { error("Save failed. Ensure WT_RECORDED_BY is an existing users.id.", e); }
    }

    void deleteAttendance() {
        int row = selected(attendanceTable); if (row < 0) return;
        Object idObj = attendanceModel.getValueAt(row,0);
        if (idObj == null || idObj.toString().isBlank()) {
            info("This row has not been saved yet. Use Save class attendance first."); return;
        }
        int id = Integer.parseInt(idObj.toString());
        if (JOptionPane.showConfirmDialog(frame, "Delete attendance record " + id + "?", "Confirm delete",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try (Connection c = db(); PreparedStatement ps = c.prepareStatement("DELETE FROM attendance WHERE id=?")) {
            ps.setInt(1,id); ps.executeUpdate(); loadAttendanceHistory(); loadReports(); loadOverview();
        } catch (Exception e) { error("Could not delete attendance record", e); }
    }

    void loadReports() {
        if (reportsModel == null) return;
        reportsModel.setRowCount(0);
        List<String> labels = new ArrayList<>(); List<Double> rates = new ArrayList<>();
        String q = "SELECT u.full_name,c.class_code,COUNT(a.id) sessions,"
                + "SUM(a.status IN ('present','late')) attended,"
                + "ROUND(100*SUM(a.status IN ('present','late'))/NULLIF(COUNT(a.id),0),1) rate "
                + "FROM attendance a JOIN users u ON u.id=a.student_id JOIN classes c ON c.id=a.class_id "
                + "GROUP BY u.id,c.id ORDER BY u.full_name,c.class_code";
        try (Connection c = db(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) {
            while (r.next()) {
                Object v = r.getObject("rate"); double rate = v == null ? 0 : r.getDouble("rate");
                reportsModel.addRow(new Object[]{r.getString("full_name"),r.getString("class_code"),
                        r.getInt("sessions"),r.getInt("attended"),v == null ? "N/A" : rate + "%"});
                labels.add(r.getString("full_name") + " / " + r.getString("class_code")); rates.add(rate);
            }
            chart.setData(labels,rates);
        } catch (Exception e) { error("Could not load reports", e); }
    }

    JPanel formPanel(String[] labels, JComponent[] fields) {
        JPanel p = new JPanel(new GridLayout(labels.length,2,8,8));
        for (int i=0;i<labels.length;i++) { p.add(new JLabel(labels[i])); p.add(fields[i]); }
        return p;
    }
    int selected(JTable table) {
        if (table == null || table.getSelectedRow() < 0) { info("Select a row first."); return -1; }
        return table.convertRowIndexToModel(table.getSelectedRow());
    }
    void info(String s) { JOptionPane.showMessageDialog(frame,s,"Smart Attendance",JOptionPane.INFORMATION_MESSAGE); }
    void error(String s, Exception e) { JOptionPane.showMessageDialog(frame,s + "\n\n" + e.getMessage(),"Smart Attendance",JOptionPane.ERROR_MESSAGE); }

    static class Item {
        final int id; final String code, name;
        Item(int id,String code,String name) { this.id=id;this.code=code;this.name=name; }
        public String toString() { return code.isEmpty() ? name + " (ID " + id + ")" : code + " — " + name; }
    }

    static class BarChart extends JPanel {
        List<String> labels = new ArrayList<>(); List<Double> values = new ArrayList<>();
        BarChart() { setPreferredSize(new Dimension(700,300)); setBackground(Color.WHITE); }
        void setData(List<String> l,List<Double> v) { labels=new ArrayList<>(l); values=new ArrayList<>(v); repaint(); }
        protected void paintComponent(Graphics g) {
            super.paintComponent(g); Graphics2D x=(Graphics2D)g.create();
            x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int left=55,top=20,w=getWidth()-75,h=getHeight()-65;
            x.setColor(new Color(220,226,235));
            for(int pct=0;pct<=100;pct+=20){int y=top+h-h*pct/100;x.drawLine(left,y,left+w,y);x.setColor(Color.DARK_GRAY);x.drawString(pct+"%",12,y+4);x.setColor(new Color(220,226,235));}
            int n=Math.min(values.size(),Math.max(1,w/36));
            if(values.isEmpty()){x.setColor(Color.DARK_GRAY);x.drawString("No report data yet.",left+10,top+20);}
            else {int gap=5,bw=Math.max(4,(w-gap*n)/n);for(int i=0;i<n;i++){double val=Math.min(100,Math.max(0,values.get(i)));int bh=(int)(h*val/100);int bx=left+i*(bw+gap),by=top+h-bh;x.setColor(BLUE);x.fillRoundRect(bx,by,bw,bh,6,6);x.setColor(Color.DARK_GRAY);x.setFont(new Font("SansSerif",Font.PLAIN,9));x.drawString(String.format(Locale.US,"%.0f%%",val),bx,Math.max(top+10,by-3));}}
            x.setColor(Color.DARK_GRAY);x.drawString("Attendance rate by student/class · showing "+n+" of "+values.size(),left,getHeight()-18);x.dispose();
        }
    }

}
