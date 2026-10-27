import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

/**
 * Java Swing dashboard connected to the existing WT smart_attendance database.
 * Configure WT_DB_URL, WT_DB_USER, WT_DB_PASSWORD and WT_RECORDED_BY as needed.
 */
public class AttendanceAnalytics {
    private static final String DB_URL = System.getenv().getOrDefault(
        "WT_DB_URL", "jdbc:mariadb://127.0.0.1:3306/smart_attendance");
    private static final String DB_USER = System.getenv().getOrDefault("WT_DB_USER", "root");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("WT_DB_PASSWORD", "");
    // Must be an existing users.id allowed by the attendance.recorded_by foreign key.
    private static final int RECORDED_BY = Integer.parseInt(
        System.getenv().getOrDefault("WT_RECORDED_BY", "2"));

    private final JFrame frame = new JFrame("Smart Attendance Analytics");
    private final JLabel summary = new JLabel("Loading database…");
    private final JComboBox<ClassItem> classBox = new JComboBox<>();
    private final JTextField dateField = new JTextField(LocalDate.now().toString(), 10);
    private final DefaultTableModel attendanceModel = new DefaultTableModel(
        new Object[]{"Student ID", "Student", "Email", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return col == 3; }
    };
    private final JTable attendanceTable = new JTable(attendanceModel);
    private final DefaultTableModel reportModel = new DefaultTableModel(
        new Object[]{"Student", "Class", "Sessions", "Attended", "Rate"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable reportTable = new JTable(reportModel);
    private final JLabel chartInfo = new JLabel("Attendance rate overview");
    private final BarChart chart = new BarChart();

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AttendanceAnalytics().start());
    }

    private void start() {
        try (Connection ignored = connect()) {
            // Connection test only. All reads and writes below use short-lived connections.
        } catch (SQLException ex) {
            showError("Cannot connect to the WT database.\n\nCheck that XAMPP MySQL is running and verify "
                + "WT_DB_URL / WT_DB_USER / WT_DB_PASSWORD.\n\nDetails: " + ex.getMessage());
            return;
        }

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1120, 720);
        frame.setMinimumSize(new Dimension(900, 600));
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        root.setBackground(new Color(245, 247, 251));
        frame.setContentPane(root);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Smart Attendance Analytics");
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        summary.setFont(new Font("SansSerif", Font.PLAIN, 13));
        header.add(title, BorderLayout.NORTH);
        header.add(summary, BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Dashboard", dashboardPanel());
        tabs.addTab("Mark Attendance", attendancePanel());
        tabs.addTab("Reports & Graphs", reportsPanel());
        root.add(tabs, BorderLayout.CENTER);

        attendanceTable.setRowHeight(28);
        attendanceTable.setFillsViewportHeight(true);
        attendanceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        reportTable.setRowHeight(26);
        reportTable.setFillsViewportHeight(true);

        JComboBox<String> statusEditor = new JComboBox<>(new String[]{"present", "absent", "late", "excused"});
        attendanceTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(statusEditor));

        refreshAll();
        frame.setVisible(true);
    }

    private JPanel dashboardPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 8, 8, 8));
        panel.setBackground(Color.WHITE);
        JLabel note = new JLabel("<html><h2>Live database overview</h2>"
            + "This dashboard reads the same users, classes, enrollments, and attendance records "
            + "as your WT PHP application.<br>Present and Late count as attended, matching the website.</html>");
        panel.add(note, BorderLayout.NORTH);
        panel.add(chart, BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh data");
        refresh.addActionListener(e -> refreshAll());
        panel.add(refresh, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel attendancePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 8, 8, 8));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("Class:"));
        controls.add(classBox);
        controls.add(new JLabel("Date (YYYY-MM-DD):"));
        controls.add(dateField);
        JButton load = new JButton("Load students");
        load.addActionListener(e -> loadAttendance());
        controls.add(load);
        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(attendanceTable), BorderLayout.CENTER);
        JButton save = new JButton("Save attendance to database");
        save.addActionListener(e -> saveAttendance());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(save);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel reportsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 8, 8, 8));
        chartInfo.setFont(new Font("SansSerif", Font.BOLD, 16));
        panel.add(chartInfo, BorderLayout.NORTH);
        panel.add(new JScrollPane(reportTable), BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh reports");
        refresh.addActionListener(e -> loadReports());
        panel.add(refresh, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshAll() {
        try {
            loadClasses();
            loadDashboard();
            loadAttendance();
            loadReports();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void loadClasses() {
        ClassItem previouslySelected = (ClassItem) classBox.getSelectedItem();
        classBox.removeAllItems();
        String sql = "SELECT id, class_code, class_name FROM classes ORDER BY class_code";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) classBox.addItem(new ClassItem(
                rs.getInt("id"), rs.getString("class_code"), rs.getString("class_name")));
        } catch (SQLException ex) {
            throw new RuntimeException("Could not load classes: " + ex.getMessage(), ex);
        }
        if (previouslySelected != null) {
            for (int i = 0; i < classBox.getItemCount(); i++) {
                if (classBox.getItemAt(i).id == previouslySelected.id) {
                    classBox.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void loadDashboard() {
        String sql = "SELECT "
            + "(SELECT COUNT(*) FROM users WHERE role='student' AND is_active=1) students, "
            + "(SELECT COUNT(*) FROM classes) classes, "
            + "(SELECT COUNT(*) FROM attendance) records, "
            + "(SELECT COUNT(*) FROM attendance WHERE attendance_date=CURDATE()) today, "
            + "(SELECT ROUND(100*SUM(status IN ('present','late'))/NULLIF(COUNT(*),0),1) "
            + "FROM attendance) rate";
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                summary.setText("Students: " + rs.getInt("students") + "   |   Classes: "
                    + rs.getInt("classes") + "   |   Attendance records: " + rs.getInt("records")
                    + "   |   Today: " + rs.getInt("today") + "   |   Overall attended rate: "
                    + (rs.getObject("rate") == null ? "N/A" : rs.getString("rate") + "%"));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Could not load dashboard: " + ex.getMessage(), ex);
        }
    }

    private void loadAttendance() {
        ClassItem selected = (ClassItem) classBox.getSelectedItem();
        if (selected == null) return;
        LocalDate date;
        try { date = LocalDate.parse(dateField.getText().trim()); }
        catch (Exception ex) {
            showError("Enter a valid date in YYYY-MM-DD format.");
            return;
        }
        attendanceModel.setRowCount(0);
        String sql = "SELECT u.id, u.full_name, u.email, COALESCE(a.status,'present') status "
            + "FROM enrollments e JOIN users u ON u.id=e.student_id "
            + "LEFT JOIN attendance a ON a.class_id=e.class_id AND a.student_id=e.student_id "
            + "AND a.attendance_date=? WHERE e.class_id=? AND u.role='student' AND u.is_active=1 "
            + "ORDER BY u.full_name";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            ps.setInt(2, selected.id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) attendanceModel.addRow(new Object[]{
                    rs.getInt("id"), rs.getString("full_name"), rs.getString("email"), rs.getString("status")
                });
            }
        } catch (SQLException ex) {
            showError("Could not load attendance: " + ex.getMessage());
        }
    }

    private void saveAttendance() {
        ClassItem selected = (ClassItem) classBox.getSelectedItem();
        if (selected == null) { showError("Select a class first."); return; }
        LocalDate date;
        try { date = LocalDate.parse(dateField.getText().trim()); }
        catch (Exception ex) { showError("Enter a valid date in YYYY-MM-DD format."); return; }

        String sql = "INSERT INTO attendance(class_id,student_id,attendance_date,status,recorded_by) "
            + "VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status),recorded_by=VALUES(recorded_by)";
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int row = 0; row < attendanceModel.getRowCount(); row++) {
                    int studentId = Integer.parseInt(attendanceModel.getValueAt(row, 0).toString());
                    String status = attendanceModel.getValueAt(row, 3).toString().toLowerCase(Locale.ROOT);
                    if (!Set.of("present", "absent", "late", "excused").contains(status))
                        throw new SQLException("Invalid status: " + status);
                    ps.setInt(1, selected.id);
                    ps.setInt(2, studentId);
                    ps.setDate(3, java.sql.Date.valueOf(date));
                    ps.setString(4, status);
                    ps.setInt(5, RECORDED_BY);
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
            JOptionPane.showMessageDialog(frame, "Attendance saved to smart_attendance.");
            refreshAll();
        } catch (Exception ex) {
            showError("Attendance was not saved.\nCheck WT_RECORDED_BY is an existing users.id.\n\n" + ex.getMessage());
        }
    }

    private void loadReports() {
        reportModel.setRowCount(0);
        List<Double> rates = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        String sql = "SELECT u.full_name,c.class_code,COUNT(a.id) sessions,"
            + "SUM(a.status IN ('present','late')) attended,"
            + "ROUND(100*SUM(a.status IN ('present','late'))/NULLIF(COUNT(a.id),0),1) rate "
            + "FROM attendance a JOIN users u ON u.id=a.student_id "
            + "JOIN classes c ON c.id=a.class_id "
            + "GROUP BY u.id,c.id ORDER BY u.full_name,c.class_code";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Object rateValue = rs.getObject("rate");
                double rate = rateValue == null ? 0.0 : rs.getDouble("rate");
                reportModel.addRow(new Object[]{rs.getString("full_name"), rs.getString("class_code"),
                    rs.getInt("sessions"), rs.getInt("attended"), rateValue == null ? "N/A" : rate + "%"});
                rates.add(rate);
                labels.add(rs.getString("full_name") + " / " + rs.getString("class_code"));
            }
            chart.setData(labels, rates);
            chartInfo.setText("Attendance rate by student and class (" + rates.size() + " summaries)");
        } catch (SQLException ex) {
            showError("Could not load reports: " + ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Attendance Analytics", JOptionPane.ERROR_MESSAGE);
    }

    private static final class ClassItem {
        final int id;
        final String code, name;
        ClassItem(int id, String code, String name) { this.id = id; this.code = code; this.name = name; }
        @Override public String toString() { return code + " — " + name; }
    }

    private static final class BarChart extends JPanel {
        private List<String> labels = new ArrayList<>();
        private List<Double> values = new ArrayList<>();
        BarChart() { setPreferredSize(new Dimension(700, 360)); setBackground(Color.WHITE); }
        void setData(List<String> labels, List<Double> values) {
            this.labels = new ArrayList<>(labels);
            this.values = new ArrayList<>(values);
            repaint();
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int left = 55, right = 20, top = 24, bottom = 58;
            int w = getWidth() - left - right, h = getHeight() - top - bottom;
            g2.setColor(new Color(225, 230, 238));
            for (int p = 0; p <= 100; p += 20) {
                int y = top + h - (h * p / 100);
                g2.drawLine(left, y, left + w, y);
                g2.setColor(new Color(80, 88, 100));
                g2.drawString(p + "%", 12, y + 5);
                g2.setColor(new Color(225, 230, 238));
            }
            if (values.isEmpty()) {
                g2.setColor(Color.DARK_GRAY);
                g2.drawString("No attendance data yet.", left + 20, top + 30);
                g2.dispose();
                return;
            }
            int visible = Math.min(values.size(), Math.max(1, w / 34));
            int gap = 5, barW = Math.max(5, (w - gap * visible) / visible);
            for (int i = 0; i < visible; i++) {
                double val = Math.max(0, Math.min(100, values.get(i)));
                int bh = (int) (h * val / 100.0);
                int x = left + i * (barW + gap);
                int y = top + h - bh;
                g2.setColor(new Color(45, 112, 220));
                g2.fillRoundRect(x, y, barW, bh, 7, 7);
                g2.setColor(new Color(55, 62, 72));
                g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
                g2.drawString(String.format(Locale.US, "%.0f%%", val), x, Math.max(top + 10, y - 4));
            }
            g2.setColor(new Color(80, 88, 100));
            g2.drawString("Showing " + visible + " of " + values.size() + " student/class summaries", left, getHeight() - 18);
            g2.dispose();
        }
    }
}
