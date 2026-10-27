import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

/**
 * Environment variables:
 * WT_DB_URL       default jdbc:mariadb://127.0.0.1:3306/smart_attendance
 * WT_DB_USER      default root
 * WT_DB_PASSWORD  default empty
 * WT_RECORDED_BY  default 2; must be an existing user ID for attendance writes
 */
public class AttendanceAnalytics {
    // Connection settings can be overridden with environment variables; defaults are for local XAMPP/MariaDB.
    static final String DB_URL = System.getenv().getOrDefault("WT_DB_URL",
            "jdbc:mariadb://127.0.0.1:3306/smart_attendance");
    static final String DB_USER = System.getenv().getOrDefault("WT_DB_USER", "root");
    static final String DB_PASSWORD = System.getenv().getOrDefault("WT_DB_PASSWORD", "");
    static final int RECORDED_BY = Integer.parseInt(System.getenv().getOrDefault("WT_RECORDED_BY", "2"));
    // Shared visual/status constants. STATUSES must match the database ENUM values.
    static final Color BLUE = new Color(35, 91, 180);
    static final String[] STATUSES = {"present", "absent", "late", "excused"};

    // References to GUI components are fields because several methods update them later.
    JFrame frame;
    JLabel overview = new JLabel("Connecting...");
    DefaultTableModel usersModel, classesModel, attendanceModel, reportsModel;
    JTable usersTable, classesTable, attendanceTable, reportsTable;
    JComboBox<Item> attendanceClass = new JComboBox<>();
    JTextField attendanceDate = new JTextField(LocalDate.now().toString(), 10);
    BarChart chart = new BarChart();

    // DATABASE HELPER: opens a JDBC connection using the configuration above.
    // Callers use try-with-resources so the connection closes automatically.
    static Connection db() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    // PROGRAM ENTRY POINT: SwingUtilities.invokeLater starts GUI work on Swing's event-dispatch thread.
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AttendanceAnalytics().launch());
    }

    // BUILDS THE MAIN WINDOW. First test the database connection, then create the sidebar, pages and navigation.
    void launch() {
        // Set the appearance before constructing components.
        installModernTheme();
        // A tiny SELECT 1 query checks that MariaDB is reachable before showing the application.
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

        frame = new JFrame("Attendly | Smart Attendance");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1360, 850);
        frame.setMinimumSize(new Dimension(1050, 680));
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(246, 248, 252));
        frame.setContentPane(root);

        JPanel sidebar = new JPanel();
        sidebar.setPreferredSize(new Dimension(218, 0));
        sidebar.setBackground(new Color(18, 28, 49));
        sidebar.setBorder(BorderFactory.createEmptyBorder(22, 14, 20, 14));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        JLabel brand = new JLabel("  ◈  ATTENDLY");
        brand.setForeground(Color.WHITE);
        brand.setFont(new Font("SansSerif", Font.BOLD, 21));
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(brand);
        JLabel tagline = new JLabel("  SMART ATTENDANCE");
        tagline.setForeground(new Color(148, 163, 184));
        tagline.setFont(new Font("SansSerif", Font.BOLD, 10));
        tagline.setBorder(BorderFactory.createEmptyBorder(5, 0, 28, 0));
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(tagline);

        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setBackground(new Color(246, 248, 252));
        JPanel topbar = new JPanel(new BorderLayout(10, 8));
        topbar.setBackground(Color.WHITE);
        topbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(229, 234, 242)),
                BorderFactory.createEmptyBorder(18, 26, 18, 26)));
        JLabel title = new JLabel("Overview");
        title.setFont(new Font("SansSerif", Font.BOLD, 25));
        title.setForeground(new Color(23, 37, 63));
        overview.setFont(new Font("SansSerif", Font.PLAIN, 12));
        overview.setForeground(new Color(100, 116, 139));
        topbar.add(title, BorderLayout.NORTH);
        topbar.add(overview, BorderLayout.SOUTH);
        content.add(topbar, BorderLayout.NORTH);

        // CardLayout keeps all pages in one area and shows one page at a time.
        JPanel pageHost = new JPanel(new CardLayout());
        pageHost.setBackground(new Color(246, 248, 252));
        String[] pageNames = {"Dashboard", "Users & Students", "Classes", "Attendance", "Reports"};
        JPanel[] pages = {dashboard(), usersPanel(), classesPanel(), attendancePanel(), reportsPanel()};
        JLabel[] pageTitle = {title};
        CardLayout cardLayout = (CardLayout) pageHost.getLayout();
        for (int i = 0; i < pages.length; i++) {
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setBackground(new Color(246, 248, 252));
            wrap.setBorder(BorderFactory.createEmptyBorder(20, 24, 22, 24));
            wrap.add(pages[i], BorderLayout.CENTER);
            pageHost.add(wrap, pageNames[i]);
            final String pageName = pageNames[i];
            JButton nav = navButton(pageName, i == 0);
            // Event listener: when clicked, show the selected page and update its title/style.
            nav.addActionListener(e -> {
                cardLayout.show(pageHost, pageName);
                title.setText(pageName.equals("Users & Students") ? "Users & Students" :
                        pageName.equals("Classes") ? "Classes & Enrolments" :
                        pageName.equals("Attendance") ? "Attendance register" :
                        pageName.equals("Reports") ? "Reports & analytics" : "Overview");
                for (Component c : sidebar.getComponents()) {
                    if (c instanceof JButton) styleNav((JButton)c, ((JButton)c) == nav);
                }
                if (pageName.equals("Attendance")) loadAttendanceHistory();
                if (pageName.equals("Reports")) loadReports();
            });
            sidebar.add(nav);
            sidebar.add(Box.createVerticalStrut(7));
        }
        sidebar.add(Box.createVerticalGlue());
        JLabel dbLabel = new JLabel("●  DATABASE CONNECTED");
        dbLabel.setForeground(new Color(110, 231, 183));
        dbLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        dbLabel.setBorder(BorderFactory.createEmptyBorder(10, 4, 0, 0));
        sidebar.add(dbLabel);

        content.add(pageHost, BorderLayout.CENTER);
        root.add(sidebar, BorderLayout.WEST);
        root.add(content, BorderLayout.CENTER);

        frame.setVisible(true);
        refreshAll();
    }

    // DASHBOARD TAB: shows a short summary and a custom attendance-rate chart.
    JPanel dashboard() {
        JPanel p = new JPanel(new BorderLayout(16, 16));
        p.setOpaque(false);
        JPanel intro = new JPanel(new BorderLayout(8, 8));
        intro.setBackground(Color.WHITE);
        intro.setBorder(cardBorder());
        JLabel heading = new JLabel("Good day. Here is your attendance snapshot.");
        heading.setFont(new Font("SansSerif", Font.BOLD, 19));
        heading.setForeground(new Color(23, 37, 63));
        JLabel sub = new JLabel("Live data from your WT MySQL database · Present and Late count as attended");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sub.setForeground(new Color(100, 116, 139));
        JPanel text = new JPanel(new GridLayout(2,1,0,5));
        text.setOpaque(false);
        text.add(heading); text.add(sub);
        intro.add(text, BorderLayout.CENTER);
        JButton refresh = primaryButton("↻  Refresh data");
        refresh.addActionListener(e -> refreshAll());
        intro.add(refresh, BorderLayout.EAST);

        JPanel chartCard = new JPanel(new BorderLayout(8,8));
        chartCard.setBackground(Color.WHITE);
        chartCard.setBorder(cardBorder());
        JLabel chartTitle = new JLabel("Attendance rate by student and class");
        chartTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        chartTitle.setForeground(new Color(23, 37, 63));
        chartCard.add(chartTitle, BorderLayout.NORTH);
        chartCard.add(chart, BorderLayout.CENTER);
        p.add(intro, BorderLayout.NORTH);
        p.add(chartCard, BorderLayout.CENTER);
        return p;
    }

    // USERS TAB: creates the users table and buttons for add, edit, activate/deactivate and delete.
    JPanel usersPanel() {
        usersModel = model("ID", "Full name", "Email", "Role", "Active");
        usersTable = new JTable(usersModel);
        usersTable.setRowHeight(25);
        JPanel p = standardTablePanel(usersTable);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("＋  Add user");
        JButton edit = new JButton("Edit selected");
        JButton toggle = new JButton("Toggle active");
        JButton delete = new JButton("Delete selected");
        JButton reload = new JButton("Refresh");
        // ActionListeners connect button clicks to the matching methods.
        add.addActionListener(e -> addUser());
        edit.addActionListener(e -> editUser());
        toggle.addActionListener(e -> toggleUser());
        delete.addActionListener(e -> deleteUser());
        reload.addActionListener(e -> loadUsers());
        actions.add(add); actions.add(edit); actions.add(toggle); actions.add(delete); actions.add(reload);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    // CLASSES TAB: displays classes and teacher names, and provides student-enrolment actions.
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

    // ATTENDANCE TAB: lets the user choose a class/date, load students, edit statuses and save records.
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

    // REPORTS TAB: displays calculated attendance statistics and offers a refresh button.
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

    // SHARED TABLE STYLING: puts any JTable inside a styled panel and a scroll pane.
    JPanel standardTablePanel(JTable table) {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBackground(Color.WHITE);
        p.setBorder(cardBorder());
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.setForeground(new Color(51, 65, 85));
        table.setBackground(Color.WHITE);
        table.setGridColor(new Color(235, 239, 245));
        table.setSelectionBackground(new Color(219, 234, 254));
        table.setSelectionForeground(new Color(23, 37, 63));
        table.setRowHeight(32);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.getTableHeader().setForeground(new Color(71, 85, 105));
        table.getTableHeader().setBackground(new Color(246, 248, 252));
        table.getTableHeader().setPreferredSize(new Dimension(0, 38));
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(232, 237, 244)));
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    // UI THEME: configures Swing's Nimbus look-and-feel and default component colours/fonts.
    void installModernTheme() {
        try { for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels())
            if ("Nimbus".equals(info.getName())) { UIManager.setLookAndFeel(info.getClassName()); break; }
        } catch (Exception ignored) {}
        UIManager.put("control", new Color(246, 248, 252));
        UIManager.put("info", Color.WHITE);
        UIManager.put("nimbusBase", new Color(35, 91, 180));
        UIManager.put("nimbusBlueGrey", new Color(226, 232, 240));
        UIManager.put("text", new Color(30, 41, 59));
        UIManager.put("Button.font", new Font("SansSerif", Font.BOLD, 12));
        UIManager.put("Label.font", new Font("SansSerif", Font.PLAIN, 13));
        UIManager.put("TextField.font", new Font("SansSerif", Font.PLAIN, 13));
        UIManager.put("ComboBox.font", new Font("SansSerif", Font.PLAIN, 13));
    }

    // Creates one sidebar navigation button; the active page is styled differently.
    JButton navButton(String text, boolean active) {
        JButton b = new JButton("   " + (text.equals("Dashboard") ? "⌂" :
                text.startsWith("Users") ? "♙" : text.equals("Classes") ? "▦" :
                text.equals("Attendance") ? "✓" : "▤") + "    " + text);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        b.setPreferredSize(new Dimension(188, 44));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        styleNav(b, active);
        return b;
    }

    // Applies selected/unselected colours and borders to a navigation button.
    void styleNav(JButton b, boolean active) {
        b.setOpaque(true);
        b.setBackground(active ? new Color(39, 70, 120) : new Color(18, 28, 49));
        b.setForeground(active ? Color.WHITE : new Color(190, 202, 220));
        b.setFont(new Font("SansSerif", active ? Font.BOLD : Font.PLAIN, 13));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(active ? new Color(53, 95, 158) : new Color(18, 28, 49), 1, true),
                BorderFactory.createEmptyBorder(0, 6, 0, 6)));
    }

    // Creates the blue-styled primary action button used on the dashboard.
    JButton primaryButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(new Color(35, 91, 180));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(35, 91, 180), 1, true),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        return b;
    }

    // Returns a reusable border with a thin outline and inner padding for cards.
    javax.swing.border.Border cardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 234, 242), 1, true),
                BorderFactory.createEmptyBorder(16, 16, 16, 16));
    }

    // Creates a table model with the given column headings and no initial rows.
    // Cells are read-only in the table; edits are done through the app's buttons/dialogs.
    DefaultTableModel model(String... cols) {
        return new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
    }

    // Reloads every screen's data from the database so the GUI reflects current records.
    void refreshAll() {
        try {
            loadUsers(); loadClasses(); loadAttendanceHistory(); loadReports(); loadOverview();
        } catch (Exception e) { error("Refresh failed", e); }
    }

    // Runs aggregate COUNT queries for the dashboard summary (students, teachers, classes, etc.).
    void loadOverview() throws SQLException {
        // Subqueries each calculate one dashboard metric and return them as a single result row.
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

    // Reads user details from MariaDB and fills the Users JTable model.
    void loadUsers() {
        if (usersModel == null) return;
        usersModel.setRowCount(0);
        // SELECT retrieves only the columns needed for the visible table; password hashes are not displayed.
        String q = "SELECT id,full_name,email,role,is_active FROM users ORDER BY role,full_name";
        try (Connection c = db(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) {
            while (r.next()) usersModel.addRow(new Object[]{r.getInt("id"), r.getString("full_name"),
                    r.getString("email"), r.getString("role"), r.getInt("is_active") == 1 ? "Active" : "Disabled"});
        } catch (Exception e) { error("Could not load users", e); }
    }

    // Opens a form, validates the values, hashes the password, and INSERTs a new user.
    void addUser() {
        JTextField name = new JTextField();
        JTextField email = new JTextField();
        JPasswordField password = new JPasswordField();
        JComboBox<String> role = new JComboBox<>(new String[]{"student", "teacher", "admin"});
        JPanel form = formPanel(new String[]{"Full name", "Email", "Password (min 6 chars)", "Role"},
                new JComponent[]{name, email, password, role});
        if (JOptionPane.showConfirmDialog(frame, form, "Create account", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        String n = name.getText().trim(), em = email.getText().trim(), pw = new String(password.getPassword());
        if (n.length() < 2 || em.isBlank() || pw.length() < 6) {
            info("Enter a full name, email, and password of at least 6 characters."); return;
        }
        try {
            // Never store the raw password. Store a salted bcrypt hash instead.
            String hash = bcryptHash(pw);
            // PreparedStatement placeholders (?) keep user input separate from SQL syntax.
            try (Connection c = db(); PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO users(full_name,email,password,role) VALUES(?,?,?,?)")) {
                ps.setString(1, n); ps.setString(2, em); ps.setString(3, hash); ps.setString(4, role.getSelectedItem().toString());
                ps.executeUpdate();
            }
            loadUsers(); loadClasses(); loadOverview();
            info("Account created successfully.");
        } catch (Exception e) { error("Could not create account. Check that the email is unique and BCrypt dependency is installed.", e); }
    }

    // Uses jBCrypt via reflection so new passwords are stored as bcrypt hashes, not plain text.
    String bcryptHash(String password) throws Exception {
        // Uses jBCrypt to create hashes compatible with PHP password_verify().
        Class<?> bcrypt = Class.forName("org.mindrot.jbcrypt.BCrypt");
        Object salt = bcrypt.getMethod("gensalt", int.class).invoke(null, 10);
        return (String) bcrypt.getMethod("hashpw", String.class, String.class).invoke(null, password, salt);
    }

    // Updates the selected user's name, email and role using a parameterised UPDATE query.
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

    // Switches is_active between 1 (active) and 0 (disabled) for the selected user.
    void toggleUser() {
        int row = selected(usersTable); if (row < 0) return;
        int id = Integer.parseInt(usersModel.getValueAt(row, 0).toString());
        int active = usersModel.getValueAt(row, 4).toString().equals("Active") ? 0 : 1;
        try (Connection c = db(); PreparedStatement ps = c.prepareStatement("UPDATE users SET is_active=? WHERE id=?")) {
            ps.setInt(1, active); ps.setInt(2, id); ps.executeUpdate(); loadUsers(); loadOverview();
        } catch (Exception e) { error("Could not change account status", e); }
    }

    // Confirms then deletes a user. Foreign-key relationships may prevent unsafe deletion.
    void deleteUser() {
        int row = selected(usersTable); if (row < 0) return;
        int id = Integer.parseInt(usersModel.getValueAt(row, 0).toString());
        if (JOptionPane.showConfirmDialog(frame, "Delete user ID " + id + "?\nThe database foreign keys may prevent deleting users referenced by classes or attendance.",
                "Confirm delete", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try (Connection c = db(); PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id=?")) {
            ps.setInt(1, id); ps.executeUpdate(); loadUsers(); loadClasses(); loadAttendanceHistory(); loadOverview();
        } catch (Exception e) { error("Could not delete user. Referenced users must be reassigned or retained.", e); }
    }

    // Loads classes with teacher names and enrolment counts using JOIN and GROUP BY.
    void loadClasses() {
        if (classesModel == null) return;
        classesModel.setRowCount(0);
        // JOIN attaches the teacher name; LEFT JOIN keeps classes even if they have zero enrolled students.
        String q = "SELECT c.id,c.class_code,c.class_name,u.full_name teacher_name,COUNT(e.student_id) students "
                + "FROM classes c JOIN users u ON u.id=c.teacher_id LEFT JOIN enrollments e ON e.class_id=c.id "
                + "GROUP BY c.id,c.class_code,c.class_name,u.full_name ORDER BY c.class_code";
        try (Connection c = db(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) {
            while (r.next()) classesModel.addRow(new Object[]{r.getInt("id"), r.getString("class_code"),
                    r.getString("class_name"), r.getString("teacher_name"), r.getInt("students")});
            reloadClassChoices(c);
        } catch (Exception e) { error("Could not load classes", e); }
    }

    // Refreshes the class dropdown while trying to keep the previously selected class.
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

    // Loads available classes and active students, then INSERTs the chosen pair into enrollments.
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

    // Validates the selected date and loads enrolled students plus their saved status for that date.
    // COALESCE displays "present" when no attendance row exists yet; saving writes the displayed status.
    void loadAttendance() {
        Item cl = (Item) attendanceClass.getSelectedItem();
        if (cl == null) return;
        try { LocalDate.parse(attendanceDate.getText().trim()); }
        catch (Exception e) { info("Enter date as YYYY-MM-DD."); return; }
        attendanceModel.setRowCount(0);
        // LEFT JOIN preserves enrolled students even when no attendance record exists for the chosen date.
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

    // Loads the latest 1,000 saved attendance rows, newest date first.
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

    // Lets the user choose a valid status and updates the selected row in the in-memory table model.
    // The change is persisted only after Save class attendance is clicked.
    void editAttendanceStatus() {
        int row = selected(attendanceTable); if (row < 0) return;
        String current = attendanceModel.getValueAt(row, 5).toString();
        JComboBox<String> status = new JComboBox<>(STATUSES); status.setSelectedItem(current);
        if (JOptionPane.showConfirmDialog(frame, status, "Set attendance status", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        attendanceModel.setValueAt(status.getSelectedItem().toString(), row, 5);
    }

    // Saves all matching class/date rows in one database transaction. Batch execution is faster;
    // commit saves all changes together, while rollback undoes them if any row fails.
    void saveClassAttendance() {
        Item cl = (Item) attendanceClass.getSelectedItem();
        if (cl == null) return;
        LocalDate date;
        try { date = LocalDate.parse(attendanceDate.getText().trim()); }
        catch (Exception e) { info("Enter date as YYYY-MM-DD."); return; }
        // Unique key on (class_id, student_id, attendance_date) makes this an upsert: insert new rows,
        // or update the existing row for that student/class/date.
        String sql = "INSERT INTO attendance(class_id,student_id,attendance_date,status,recorded_by) VALUES(?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE status=VALUES(status),recorded_by=VALUES(recorded_by)";
        try (Connection c = db()) {
            // Disable auto-commit so all attendance rows are treated as one transaction.
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
                // Apply all queued statements, then commit the transaction.
                ps.executeBatch(); c.commit();
            } catch (Exception e) { c.rollback(); throw e; } // If any write fails, undo all writes in this batch.
            finally { c.setAutoCommit(true); }
            loadAttendanceHistory(); loadReports(); loadOverview();
            info("Attendance saved to the shared smart_attendance database.");
        } catch (Exception e) { error("Save failed. Ensure WT_RECORDED_BY is an existing users.id.", e); }
    }

    // Confirms and deletes one saved attendance record by its primary-key ID.
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

    // Calculates sessions, attended sessions and percentage per student/class, then updates the chart.
    void loadReports() {
        if (reportsModel == null) return;
        reportsModel.setRowCount(0);
        List<String> labels = new ArrayList<>(); List<Double> rates = new ArrayList<>();
        // COUNT counts recorded sessions. Present and late are treated as attended; NULLIF avoids division by zero.
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

    // Builds a simple two-column form: each label is placed beside its input component.
    JPanel formPanel(String[] labels, JComponent[] fields) {
        JPanel p = new JPanel(new GridLayout(labels.length,2,8,8));
        for (int i=0;i<labels.length;i++) { p.add(new JLabel(labels[i])); p.add(fields[i]); }
        return p;
    }
    // Returns the selected row index in the table model, or -1 after showing a message if none is selected.
    int selected(JTable table) {
        if (table == null || table.getSelectedRow() < 0) { info("Select a row first."); return -1; }
        return table.convertRowIndexToModel(table.getSelectedRow());
    }
    // Shows a normal informational popup.
    void info(String s) { JOptionPane.showMessageDialog(frame,s,"Smart Attendance",JOptionPane.INFORMATION_MESSAGE); }
    // Shows an error popup and includes the exception message to help diagnose the problem.
    void error(String s, Exception e) { JOptionPane.showMessageDialog(frame,s + "\n\n" + e.getMessage(),"Smart Attendance",JOptionPane.ERROR_MESSAGE); }

    // Small helper object for dropdowns. It stores IDs separately from the human-readable label.
    static class Item {
        final int id; final String code, name;
        Item(int id,String code,String name) { this.id=id;this.code=code;this.name=name; }
        public String toString() { return code.isEmpty() ? name + " (ID " + id + ")" : code + " — " + name; }
    }

    // CUSTOM SWING CHART: draws attendance percentages directly using Java 2D graphics.
    static class BarChart extends JPanel {
        List<String> labels = new ArrayList<>(); List<Double> values = new ArrayList<>();
        BarChart() { setPreferredSize(new Dimension(700,300)); setBackground(Color.WHITE); }
        void setData(List<String> l,List<Double> v) { labels=new ArrayList<>(l); values=new ArrayList<>(v); repaint(); }
        protected void paintComponent(Graphics g) {
            super.paintComponent(g); Graphics2D x=(Graphics2D)g.create();
            // Anti-aliasing smooths the edges of the chart bars and text.
            x.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int left=55,top=20,w=getWidth()-75,h=getHeight()-65;
            x.setColor(new Color(220,226,235));
            for(int pct=0;pct<=100;pct+=20){int y=top+h-h*pct/100;x.drawLine(left,y,left+w,y);x.setColor(Color.DARK_GRAY);x.drawString(pct+"%",12,y+4);x.setColor(new Color(220,226,235));}
            int n=Math.min(values.size(),Math.max(1,w/36));
            if(values.isEmpty()){x.setColor(Color.DARK_GRAY);x.drawString("No report data yet.",left+10,top+20);}
            else {int gap=5,bw=Math.max(4,(w-gap*n)/n);for(int i=0;i<n;i++){double val=Math.min(100,Math.max(0,values.get(i)));int bh=(int)(h*val/100);int bx=left+i*(bw+gap),by=top+h-bh;x.setColor(new Color(59, 130, 246));x.fillRoundRect(bx,by,bw,bh,6,6);x.setColor(Color.DARK_GRAY);x.setFont(new Font("SansSerif",Font.PLAIN,9));x.drawString(String.format(Locale.US,"%.0f%%",val),bx,Math.max(top+10,by-3));}}
            x.setColor(Color.DARK_GRAY);x.drawString("Attendance rate by student/class · showing "+n+" of "+values.size(),left,getHeight()-18);x.dispose();
        }
    }

}
