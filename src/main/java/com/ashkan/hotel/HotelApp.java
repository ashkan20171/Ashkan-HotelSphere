package com.ashkan.hotel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;

/** Dependency-free desktop hotel management starter, Java 17. */
public final class HotelApp {
    private static final Color NAVY = new Color(19, 34, 59), TEAL = new Color(0, 151, 145), BG = new Color(244, 247, 251);
    private final Repository repository = new Repository();
    private final JFrame frame = new JFrame("Ashkan HotelSphere | مدیریت هتل");
    private final JPanel content = new JPanel(new BorderLayout());
    private final JLabel heading = new JLabel();
    private boolean persian = true;
    private JPanel sidebar;
    private String currentRole = "";
    private String currentUser = "";
    private String currentPage = "dashboard";
    private final HousekeepingService housekeeping = new HousekeepingService();
    private final StaffProfileService staffProfiles = new StaffProfileService();
    private final AttendanceService attendance = new AttendanceService();
    private final PayrollService payroll = new PayrollService();
    private final InventoryService inventory = new InventoryService();
    private final RestaurantService restaurant = new RestaurantService(inventory);
    private final EventService events = new EventService();
    private final SpaService spa = new SpaService();
    private final ParkingService parking = new ParkingService();
    private final CrmService crm = new CrmService();
    private final CampaignService campaigns = new CampaignService();
    private final ChannelManagerService channelManager = new ChannelManagerService();
    private final GuestJourneyService guestJourney = new GuestJourneyService();
    private final RevenueManagementService revenueManagement = new RevenueManagementService();
    private final MaintenanceIntelligenceService maintenance = new MaintenanceIntelligenceService();
    private final HousekeepingWorkforceService housekeepingWorkforce = new HousekeepingWorkforceService();
    private final HousekeepingQualityService housekeepingQuality = new HousekeepingQualityService();
    private final CustomerExperienceService customerExperience = new CustomerExperienceService();
    private final NotificationService notifications = new NotificationService();
    private static final long SESSION_TIMEOUT_MS = 15 * 60 * 1000L;
    private static final long SESSION_WARNING_MS = 60 * 1000L;
    private SessionTimeoutService sessionTimeout;
    private javax.swing.Timer sessionTimer;
    private java.awt.event.AWTEventListener sessionActivityListener;
    private JDialog sessionWarningDialog;

    public static void main(String[] args) { SwingUtilities.invokeLater(() -> new HotelApp().start()); }

    private void start() {
        if (!login()) return;
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1050, 690));
        frame.setSize(1220, 760);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());
        JPanel side = new JPanel(); sidebar = side; side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS)); side.setBackground(NAVY);
        side.setBorder(new EmptyBorder(25, 18, 20, 18)); side.setPreferredSize(new Dimension(230, 0));
        JLabel logo = new JLabel("ASHKAN  HOTEL"); logo.setFont(new Font("SansSerif", Font.BOLD, 20)); logo.setForeground(Color.WHITE);
        side.add(logo); side.add(Box.createVerticalStrut(34));
        nav(side, "داشبورد / Dashboard", this::dashboard);
        nav(side, "کانال‌های رزرو / Channel Manager", this::channelManagerPage);
        nav(side, "پذیرش و حساب مهمان / Guest Journey", this::guestJourneyPage);
        nav(side, "مدیریت درآمد / Revenue Management", this::revenueManagementPage);
        nav(side, "انبار و موجودی / Inventory", this::inventoryPage);
        nav(side, "رستوران و کافی‌شاپ / Restaurant", this::restaurantPage);
        nav(side, "سالن‌ها و رویدادها / Events", this::eventsPage);
        nav(side, "اسپا و تفریحات / Spa & Leisure", this::spaPage);
        nav(side, "پارکینگ و حمل‌ونقل / Parking", this::parkingPage);
        nav(side, "ارتباط با مشتریان / Hotel CRM", this::crmPage);
        nav(side, "رضایت و تجربه مهمان / Guest Experience", this::customerExperiencePage);
        nav(side, "اعلان‌ها / Notifications", this::notificationPage);
        nav(side, "کمپین و تخفیف / Campaigns", this::campaignPage);
        nav(side, "اتاق‌ها / Rooms", this::rooms);
        nav(side, "خانه‌داری / Housekeeping", this::housekeepingPage);
        nav(side, "برنامه نیروی خانه‌داری / Housekeeping Workforce", this::housekeepingWorkforcePage);
        nav(side, "کنترل کیفیت نظافت / Quality Control", this::housekeepingQualityPage);
        nav(side, "نگهداری هوشمند / Maintenance Intelligence", this::maintenancePage);
        nav(side, "وظایف / Tasks", this::tasksPage);
        nav(side, "خدمات مهمان / Guest requests", this::guestRequestsPage);
        nav(side, "تاریخچه / Audit", this::auditPage);
        nav(side, "عملکرد کارکنان / Staff performance", this::staffPerformancePage);
        nav(side, "حضور و غیاب / Attendance", this::attendancePage);
        nav(side, "حقوق و دستمزد / Payroll", this::payrollPage);
        nav(side, "عملیات / Operations", this::operationsPage);
        nav(side, "رزروها / Bookings", this::bookings);
        nav(side, "مهمان‌ها / Guests", this::guests);
        nav(side, "گزارش‌ها / Reports", this::reports);
        nav(side, "پرداخت‌ها / Payments", this::payments);
        nav(side, "مالی / Finance", this::finance);
        nav(side, "بدهی‌ها / Receivables", this::receivables);
        nav(side, "FA / EN", () -> {persian = !persian; refreshNavigation(); openCurrentPage();});
        nav(side, "Backup / پشتیبان", this::backup);
        nav(side, "Password / تغییر رمز", this::changePassword);
        nav(side, "Staff / کارکنان", this::staffAccounts);
        nav(side, "CSV / خروجی گزارش", this::exportCsv);
        nav(side, "خروج / Sign out", () -> signOut(false));
        frame.add(side, BorderLayout.WEST);
        JPanel main = new JPanel(new BorderLayout()); main.setBackground(BG);
        heading.setFont(new Font("SansSerif", Font.BOLD, 25)); heading.setBorder(new EmptyBorder(24, 28, 15, 28));
        main.add(heading, BorderLayout.NORTH); content.setBackground(BG); content.setBorder(new EmptyBorder(5, 28, 28, 28)); main.add(content, BorderLayout.CENTER);
        frame.add(main, BorderLayout.CENTER); refreshNavigation(); dashboard(); frame.setVisible(true);
        startSessionMonitoring();
    }
    private void startSessionMonitoring() {
        sessionTimeout = new SessionTimeoutService(SESSION_TIMEOUT_MS, SESSION_WARNING_MS, System.currentTimeMillis());
        sessionActivityListener = event -> {
            if (event instanceof java.awt.event.InputEvent || event instanceof java.awt.event.WindowEvent) {
                sessionTimeout.recordActivity(System.currentTimeMillis());
                if (sessionWarningDialog != null && sessionWarningDialog.isVisible()) {
                    sessionWarningDialog.dispose();
                    sessionWarningDialog = null;
                }
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(sessionActivityListener,
                AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK |
                AWTEvent.MOUSE_WHEEL_EVENT_MASK);
        sessionTimer = new javax.swing.Timer(1000, event -> {
            long now = System.currentTimeMillis();
            if (sessionTimeout.isExpired(now)) {
                signOut(true);
            } else if (sessionTimeout.shouldWarn(now)) {
                showSessionWarning();
            }
        });
        sessionTimer.start();
    }

    private void showSessionWarning() {
        if (sessionWarningDialog != null && sessionWarningDialog.isDisplayable()) return;
        sessionWarningDialog = new JDialog(frame, tr("پایان نشست", "Session expiring"), false);
        JPanel body = new JPanel(new BorderLayout(12, 12));
        body.setBorder(new EmptyBorder(18, 18, 18, 18));
        body.add(new JLabel(tr("به دلیل عدم فعالیت، نشست شما تا یک دقیقه دیگر بسته می‌شود.",
                "Your session will expire in one minute due to inactivity.")), BorderLayout.CENTER);
        JButton continueButton = new JButton(tr("ادامه کار", "Continue session"));
        continueButton.addActionListener(e -> {
            sessionTimeout.recordActivity(System.currentTimeMillis());
            if (sessionWarningDialog != null) sessionWarningDialog.dispose();
            sessionWarningDialog = null;
        });
        body.add(continueButton, BorderLayout.SOUTH);
        sessionWarningDialog.setContentPane(body);
        sessionWarningDialog.pack();
        sessionWarningDialog.setLocationRelativeTo(frame);
        sessionWarningDialog.setAlwaysOnTop(true);
        sessionWarningDialog.setVisible(true);
    }

    private void signOut(boolean dueToInactivity) {
        if (sessionTimer != null) { sessionTimer.stop(); sessionTimer = null; }
        if (sessionActivityListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(sessionActivityListener);
            sessionActivityListener = null;
        }
        if (sessionWarningDialog != null) { sessionWarningDialog.dispose(); sessionWarningDialog = null; }
        if (!currentUser.isBlank()) {
            try { auth.recordSignOut(currentUser); } catch (RuntimeException ignored) { }
            AuditService.clearActor();
        }
        frame.dispose();
        if (dueToInactivity) JOptionPane.showMessageDialog(null,
                tr("به دلیل عدم فعالیت، برای حفاظت از اطلاعات از حساب خارج شدید.",
                   "You were signed out due to inactivity to protect hotel data."),
                tr("خروج خودکار", "Automatic sign out"), JOptionPane.INFORMATION_MESSAGE);
        new HotelApp().start();
    }

    private final AuthService auth = new AuthService();
    private boolean login() {
        JPanel form = new JPanel(new GridLayout(3, 2, 12, 12));
        JTextField account = new JTextField("Admin");
        JPasswordField password = new JPasswordField();
        form.add(new JLabel("Account / حساب")); form.add(account);
        form.add(new JLabel("Password / رمز")); form.add(password);
        form.add(new JLabel("Local accounts / حساب‌های محلی")); form.add(new JLabel("PBKDF2 protected"));
        for (int attempt = 0; attempt < 3; attempt++) {
            int answer = JOptionPane.showConfirmDialog(null, form, "Ashkan HotelSphere | Sign in", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) return false;
            String username = account.getText().trim();
            char[] supplied = password.getPassword();
            AuthService.LoginResult result;
            try { result = auth.authenticate(username, supplied); }
            catch (RuntimeException ex) { result = AuthService.LoginResult.INVALID_CREDENTIALS; }
            Arrays.fill(supplied, '\0'); password.setText("");
            if (result == AuthService.LoginResult.SUCCESS) {
                currentUser = username; currentRole = auth.roleOf(username); AuditService.setActor(username); return true;
            }
            String message = switch (result) {
                case LOCKED -> "Account temporarily locked. Try again in 5 minutes. / حساب موقتاً قفل شد؛ ۵ دقیقه دیگر تلاش کنید.";
                case DISABLED -> "This account is disabled. / این حساب غیرفعال است.";
                default -> "Invalid credentials / اطلاعات ورود نادرست است";
            };
            JOptionPane.showMessageDialog(null, message);
        }
        return false;
    }
    private void changePassword() {
        JPasswordField oldField = new JPasswordField(), newField = new JPasswordField();
        JPanel form = new JPanel(new GridLayout(2, 2, 10, 10));
        form.add(new JLabel(tr("رمز فعلی", "Current password"))); form.add(oldField);
        form.add(new JLabel(tr("رمز جدید (حداقل ۱۲ کاراکتر)", "New password (12+ characters)"))); form.add(newField);
        if (JOptionPane.showConfirmDialog(frame, form, tr("تغییر رمز", "Change password"), JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        char[] oldPassword = oldField.getPassword(), newPassword = newField.getPassword();
        try {
            auth.changePassword(currentUser, oldPassword, newPassword);
            JOptionPane.showMessageDialog(frame, tr("رمز عبور تغییر کرد", "Password changed"));
        } catch (Exception ex) { error(ex.getMessage()); }
        finally { Arrays.fill(oldPassword, '\0'); Arrays.fill(newPassword, '\0'); }
    }
    private void staffAccounts() {
        if (!requireAdmin()) return;
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JTextField search = new JTextField(18);
        JComboBox<String> statusFilter = new JComboBox<>(new String[]{"All statuses", "Active", "Disabled"});
        JComboBox<String> roleFilter = new JComboBox<>(new String[]{"All roles", "Admin", "Receptionist"});
        filters.add(new JLabel(tr("جستجو", "Search"))); filters.add(search); filters.add(statusFilter); filters.add(roleFilter);
        panel.add(filters, BorderLayout.NORTH);
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Username", "Role", "Active", "Full name", "Department", "Job title", "Phone"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable table = new JTable(model); table.setAutoCreateRowSorter(true); panel.add(new JScrollPane(table), BorderLayout.CENTER);
        JButton add = new JButton(tr("کاربر جدید", "Add account"));
        JButton edit = new JButton(tr("اطلاعات پرسنلی", "Edit profile"));
        JButton role = new JButton(tr("تغییر نقش", "Change role"));
        JButton activeButton = new JButton(tr("فعال/غیرفعال", "Toggle active"));
        JButton activity = new JButton(tr("گزارش فعالیت", "Activity log"));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        actions.add(add); actions.add(edit); actions.add(role); actions.add(activeButton); actions.add(activity);
        panel.add(actions, BorderLayout.SOUTH);
        Runnable refresh = () -> {
            model.setRowCount(0);
            String q = search.getText().strip().toLowerCase(Locale.ROOT);
            String status = (String) statusFilter.getSelectedItem(), selectedRole = (String) roleFilter.getSelectedItem();
            for (String username : auth.accounts()) {
                boolean enabled = auth.isActive(username); String userRole = auth.roleOf(username);
                if ("Active".equals(status) && !enabled || "Disabled".equals(status) && enabled) continue;
                if (!"All roles".equals(selectedRole) && !selectedRole.equals(userRole)) continue;
                StaffProfileService.Profile p = staffProfiles.find(username).orElse(new StaffProfileService.Profile(username, "", "", "", "", "", ""));
                String searchable = String.join(" ", username, userRole, p.fullName(), p.email(), p.phone(), p.department(), p.jobTitle()).toLowerCase(Locale.ROOT);
                if (!q.isEmpty() && !searchable.contains(q)) continue;
                model.addRow(new Object[]{username, userRole, enabled, p.fullName(), p.department(), p.jobTitle(), p.phone()});
            }
        };
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refresh.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refresh.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refresh.run(); }
        });
        statusFilter.addActionListener(e -> refresh.run()); roleFilter.addActionListener(e -> refresh.run());
        JDialog dialog = new JDialog(frame, tr("مدیریت کارکنان", "Staff management"), true);
        java.util.function.Supplier<String> selectedUsername = () -> {
            int viewRow = table.getSelectedRow(); if (viewRow < 0) return null;
            return (String) model.getValueAt(table.convertRowIndexToModel(viewRow), 0);
        };
        add.addActionListener(e -> {
            JTextField username = new JTextField(); JPasswordField password = new JPasswordField();
            JComboBox<String> choice = new JComboBox<>(new String[]{"Receptionist", "Admin"});
            JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
            form.add(new JLabel("Username")); form.add(username); form.add(new JLabel("Password (12+)")); form.add(password); form.add(new JLabel("Role")); form.add(choice);
            if (JOptionPane.showConfirmDialog(dialog, form, "New account", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            char[] secret = password.getPassword();
            try { auth.createAccount(currentUser, username.getText().trim(), secret, (String) choice.getSelectedItem()); refresh.run(); }
            catch (Exception ex) { error(ex.getMessage()); } finally { Arrays.fill(secret, '\0'); password.setText(""); }
        });
        edit.addActionListener(e -> {
            String username = selectedUsername.get(); if (username == null) return;
            StaffProfileService.Profile old = staffProfiles.find(username).orElse(new StaffProfileService.Profile(username, "", "", "", "", "", ""));
            JTextField fullName = new JTextField(old.fullName()), email = new JTextField(old.email()), phone = new JTextField(old.phone());
            JTextField department = new JTextField(old.department()), title = new JTextField(old.jobTitle()); JTextArea notes = new JTextArea(old.notes(), 3, 22);
            JPanel form = new JPanel(new GridLayout(6, 2, 7, 7));
            form.add(new JLabel("Full name")); form.add(fullName); form.add(new JLabel("Email")); form.add(email);
            form.add(new JLabel("Phone")); form.add(phone); form.add(new JLabel("Department")); form.add(department);
            form.add(new JLabel("Job title")); form.add(title); form.add(new JLabel("Notes")); form.add(new JScrollPane(notes));
            if (JOptionPane.showConfirmDialog(dialog, form, "Staff profile: " + username, JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            try { staffProfiles.save(new StaffProfileService.Profile(username, fullName.getText(), email.getText(), phone.getText(), department.getText(), title.getText(), notes.getText())); refresh.run(); }
            catch (Exception ex) { error(ex.getMessage()); }
        });
        role.addActionListener(e -> {
            String username = selectedUsername.get(); if (username == null) return;
            String next = (String) JOptionPane.showInputDialog(dialog, "Role", "Change role", JOptionPane.QUESTION_MESSAGE, null,
                    new String[]{"Receptionist", "Admin"}, auth.roleOf(username));
            if (next == null) return;
            try { auth.setRole(currentUser, username, next); refresh.run(); } catch (Exception ex) { error(ex.getMessage()); }
        });
        activeButton.addActionListener(e -> {
            String username = selectedUsername.get(); if (username == null) return;
            boolean currentlyActive = auth.isActive(username);
            int confirm = JOptionPane.showConfirmDialog(dialog, currentlyActive ? "Disable account " + username + "? History will be retained." : "Enable account " + username + "?", "Account status", JOptionPane.OK_CANCEL_OPTION);
            if (confirm != JOptionPane.OK_OPTION) return;
            try { auth.setActive(currentUser, username, !currentlyActive); refresh.run(); } catch (Exception ex) { error(ex.getMessage()); }
        });
        activity.addActionListener(e -> {
            String username = selectedUsername.get(); if (username == null) return;
            java.util.List<String> events = auth.securityEventsFor(username);
            JTextArea output = new JTextArea(events.isEmpty() ? "No security events recorded." : String.join("\n", events), 14, 70);
            output.setEditable(false); output.setCaretPosition(0);
            JOptionPane.showMessageDialog(dialog, new JScrollPane(output), "Security activity: " + username, JOptionPane.INFORMATION_MESSAGE);
        });
        refresh.run(); dialog.setContentPane(panel); dialog.setSize(980, 500); dialog.setLocationRelativeTo(frame); dialog.setVisible(true);
    }
    private boolean requireAdmin() {
        if ("Admin".equals(currentRole)) return true;
        error(tr("این عملیات فقط برای مدیر است", "This operation requires Admin"));
        return false;
    }
    private void exportCsv() {
        if (!requireAdmin()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("hotelsphere-bookings.csv"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try (java.io.BufferedWriter writer = Files.newBufferedWriter(chooser.getSelectedFile().toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write("Booking ID,Room,Guest ID,Check in,Check out,Status\n");
            for (Booking b : repository.bookings) {
                writer.write(String.join(",", csv(b.id), csv(b.roomId), csv(b.guestId), csv(b.start.toString()), csv(b.end.toString()), csv(b.status)));
                writer.newLine();
            }
            JOptionPane.showMessageDialog(frame, tr("گزارش ذخیره شد", "CSV report saved"));
        } catch (IOException ex) { error(ex.getMessage()); }
    }
    private static String csv(String value) { String safe = value == null ? "" : value; if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe; return "\"" + safe.replace("\"", "\"\"") + "\""; }
    private void openCurrentPage() {
        switch (currentPage) {
            case "channel-manager" -> channelManagerPage(); case "guest-journey" -> guestJourneyPage(); case "revenue-management" -> revenueManagementPage(); case "inventory" -> inventoryPage(); case "restaurant" -> restaurantPage(); case "events" -> eventsPage(); case "spa" -> spaPage(); case "parking" -> parkingPage(); case "crm" -> crmPage(); case "customer-experience" -> customerExperiencePage(); case "notifications" -> notificationPage(); case "campaigns" -> campaignPage(); case "operations" -> operationsPage(); case "audit" -> auditPage(); case "performance" -> staffPerformancePage(); case "attendance" -> attendancePage(); case "payroll" -> payrollPage(); case "requests" -> guestRequestsPage(); case "rooms" -> rooms(); case "housekeeping" -> housekeepingPage(); case "housekeeping-workforce" -> housekeepingWorkforcePage(); case "housekeeping-quality" -> housekeepingQualityPage(); case "maintenance" -> maintenancePage(); case "tasks" -> tasksPage(); case "bookings" -> bookings();
            case "guests" -> guests(); case "reports" -> reports(); case "payments" -> payments(); case "finance" -> finance(); case "receivables" -> receivables();
            default -> dashboard();
        }
    }
    private void refreshNavigation() {
        String[][] names = {{"داشبورد", "Dashboard"},{"کانال‌های رزرو", "Channel Manager"},{"پذیرش و حساب مهمان", "Guest Journey"},{"مدیریت درآمد", "Revenue Management"},{"انبار و موجودی", "Inventory"},{"رستوران و کافی‌شاپ", "Restaurant & Cafe"},{"سالن‌ها و رویدادها", "Events & Halls"},{"اسپا و تفریحات", "Spa & Leisure"},{"پارکینگ و حمل‌ونقل", "Parking & Transport"},{"ارتباط با مشتریان", "Hotel CRM"},{"رضایت و تجربه مهمان", "Guest Experience"},{"اعلان‌ها", "Notifications"},{"کمپین و تخفیف", "Campaigns"},{"اتاق‌ها", "Rooms"},{"خانه‌داری", "Housekeeping"},{"برنامه نیروی خانه‌داری", "Housekeeping Workforce"},{"کنترل کیفیت نظافت", "Housekeeping Quality"},{"نگهداری هوشمند", "Maintenance Intelligence"},{"وظایف", "Tasks"},{"خدمات مهمان", "Guest requests"},{"تاریخچه", "Audit trail"},{"عملکرد کارکنان", "Staff performance"},{"حضور و غیاب", "Attendance"},{"حقوق و دستمزد", "Payroll"},{"عملیات", "Operations"},{"رزروها", "Bookings"},{"مهمان‌ها", "Guests"},{"گزارش‌ها", "Reports"},{"پرداخت‌ها", "Payments"},{"مالی", "Finance"},{"بدهی‌ها", "Receivables"},{"زبان: فارسی / English", "Language: English / فارسی"},{"پشتیبان‌گیری", "Backup"},{"تغییر رمز", "Change password"},{"مدیریت کارکنان", "Staff accounts"},{"خروجی CSV", "Export CSV"},{"خروج", "Sign out"}};
        int index = 0;
        for (Component c : sidebar.getComponents()) {
            if (c instanceof JButton b) {
                if (index < names.length) b.setText(tr(names[index][0], names[index][1]));
                index++;
            }
        }
        sidebar.applyComponentOrientation(persian ? ComponentOrientation.RIGHT_TO_LEFT : ComponentOrientation.LEFT_TO_RIGHT);
    }
    private String tr(String fa, String en) {return persian ? fa : en;}
    private void nav(JPanel panel, String text, Runnable action) {
        JButton b = new JButton(text); b.setHorizontalAlignment(SwingConstants.LEFT); b.setForeground(Color.WHITE); b.setBackground(NAVY);
        b.setFont(new Font("SansSerif", Font.PLAIN, 14)); b.setBorder(new EmptyBorder(15, 10, 15, 8)); b.setFocusPainted(false);
        b.setMaximumSize(new Dimension(220, 52)); b.addActionListener(e -> action.run()); panel.add(b); panel.add(Box.createVerticalStrut(5));
    }
    private void show(String fa, String en, JComponent view) {
        view.applyComponentOrientation(persian ? ComponentOrientation.RIGHT_TO_LEFT : ComponentOrientation.LEFT_TO_RIGHT);
        heading.setText(tr(fa,en)); content.removeAll(); content.add(view, BorderLayout.CENTER); content.revalidate(); content.repaint();}
    private JPanel panel() {JPanel p = new JPanel(new BorderLayout(12,12)); p.setBackground(Color.WHITE); p.setBorder(new EmptyBorder(22,22,22,22)); return p;}
    private JTable table(String[] headers, List<String[]> rows) {
        DefaultTableModel model = new DefaultTableModel(headers,0) {public boolean isCellEditable(int r,int c){return false;}};
        rows.forEach(model::addRow); JTable t = new JTable(model); t.setRowHeight(36); t.setFont(new Font("SansSerif",Font.PLAIN,13));
        t.getTableHeader().setFont(new Font("SansSerif",Font.BOLD,13)); t.setSelectionBackground(new Color(201,238,234)); return t;
    }
    private JButton button(String fa,String en,Runnable run) {JButton b = new JButton(tr(fa,en)); b.setBackground(TEAL); b.setForeground(Color.WHITE); b.setFocusPainted(false); b.addActionListener(e -> run.run()); return b;}
    private void dashboard() {
        currentPage = "dashboard";
        JPanel p = panel(); JPanel stats = new JPanel(new GridLayout(1,4,15,15)); stats.setOpaque(false);
        long occupied = repository.rooms.stream().filter(r -> repository.active(r.id)).count();
        stats.add(metric(tr("کل اتاق‌ها","Total rooms"),""+repository.rooms.size()));
        stats.add(metric(tr("اشغال امروز","Occupied today"),""+occupied));
        stats.add(metric(tr("رزروها","Bookings"),""+repository.bookings.size()));
        stats.add(metric(tr("اشغال امروز ٪","Occupancy today %"), AnalyticsService.occupancyPercent(repository.rooms, repository.bookings, LocalDate.now()) + "%"));
        p.add(stats,BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(12, 12)); center.setOpaque(false);
        center.setBorder(new EmptyBorder(25, 0, 0, 0));
        center.add(new OccupancyChart(repository, persian), BorderLayout.CENTER);
        JLabel footnote = new JLabel(tr("نمودار اشغال ۷ روز آینده (رزروهای فعال)", "Next 7 days occupancy (active bookings)"));
        footnote.setForeground(NAVY); footnote.setFont(new Font("SansSerif", Font.BOLD, 15));
        center.add(footnote, BorderLayout.NORTH);
        p.add(center, BorderLayout.CENTER);
        show("داشبورد مدیریت هتل — " + currentRole,"Hotel overview — " + currentUser + " (" + currentRole + ")",p);
    }
    /** Lightweight, dependency-free occupancy chart. */
    private static final class OccupancyChart extends JPanel {
        private final Repository data;
        private final boolean fa;
        OccupancyChart(Repository data, boolean fa) { this.data = data; this.fa = fa; setOpaque(false); setPreferredSize(new Dimension(600, 290)); }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int left = 45, right = 25, top = 25, bottom = 55;
                int w = Math.max(1, getWidth() - left - right), h = Math.max(1, getHeight() - top - bottom);
                int total = data.rooms.size();
                g.setColor(new Color(220, 228, 237));
                for (int i=0; i<=4; i++) { int y = top + h - i*h/4; g.drawLine(left, y, left+w, y); }
                for (int day=0; day<7; day++) {
                    LocalDate date = LocalDate.now().plusDays(day);
                    long occupied = AnalyticsService.occupiedRooms(data.rooms, data.bookings, date);
                    double ratio = total == 0 ? 0 : Math.min(1, occupied / (double) total);
                    int bw = Math.max(12, w/12), x = left + (day*2+1)*w/14 - bw/2;
                    int bh = (int)(h*ratio);
                    g.setColor(TEAL); g.fillRoundRect(x, top+h-bh, bw, Math.max(2,bh), 10, 10);
                    g.setColor(NAVY); g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                    String label = date.getMonthValue() + "/" + date.getDayOfMonth();
                    g.drawString(label, x-4, top+h+22);
                    g.drawString(Long.toString(occupied), x+bw/2-4, top+h-bh-7);
                }
                g.setColor(NAVY); g.drawString(fa ? "اتاق" : "Rooms", 5, 18);
            } finally { g.dispose(); }
        }
    }
    private JPanel metric(String title,String value) {JPanel p = new JPanel(new GridLayout(2,1)); p.setBackground(new Color(232,246,245)); p.setBorder(new EmptyBorder(20,20,20,20)); JLabel a=new JLabel(title),b=new JLabel(value); b.setFont(new Font("SansSerif",Font.BOLD,32)); b.setForeground(NAVY); p.add(a);p.add(b);return p;}
    private void operationsPage() {
        if (!requireAdmin()) return;
        currentPage = "operations";
        JPanel page = panel();
        var summary = OperationsService.summarize(tasks.all(), guestRequests.all());
        JPanel stats = new JPanel(new GridLayout(1, 4, 12, 12));
        stats.setOpaque(false);
        stats.add(metric(tr("وظایف باز", "Open tasks"), String.valueOf(summary.openTasks())));
        stats.add(metric(tr("وظایف در حال انجام", "Tasks in progress"), String.valueOf(summary.activeTasks())));
        stats.add(metric(tr("درخواست‌های باز", "Open requests"), String.valueOf(summary.openRequests())));
        stats.add(metric(tr("درخواست‌های فوری", "Urgent requests"), String.valueOf(summary.urgentRequests())));
        page.add(stats, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(8, 8));
        JTextField search = new JTextField();
        search.putClientProperty("JTextField.placeholderText", tr("جستجو در درخواست‌ها", "Search requests"));
        JComboBox<String> status = new JComboBox<>(new String[]{"ALL", "OPEN", "IN_PROGRESS", "RESOLVED"});
        JPanel filters = new JPanel(new BorderLayout(8, 8));
        filters.add(search, BorderLayout.CENTER); filters.add(status, BorderLayout.EAST);
        center.add(filters, BorderLayout.NORTH);
        DefaultTableModel model = new DefaultTableModel(new String[]{"ID", tr("اتاق", "Room"), tr("نوع", "Category"), tr("مسئول", "Assignee"), tr("اولویت", "Priority"), tr("وضعیت", "Status")}, 0) {
            @Override public boolean isCellEditable(int r, int c) {return false;}
        };
        JTable grid = new JTable(model); grid.setRowHeight(30);
        Runnable refresh = () -> {
            model.setRowCount(0);
            for (var r : OperationsService.filterRequests(guestRequests.all(), search.getText(), String.valueOf(status.getSelectedItem())))
                model.addRow(new Object[]{r.id(), r.roomId(), r.category(), r.assignee(), r.priority(), r.status()});
        };
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {refresh.run();}
            public void removeUpdate(javax.swing.event.DocumentEvent e) {refresh.run();}
            public void changedUpdate(javax.swing.event.DocumentEvent e) {refresh.run();}
        });
        status.addActionListener(e -> refresh.run()); refresh.run();
        center.add(new JScrollPane(grid), BorderLayout.CENTER);
        page.add(center, BorderLayout.CENTER);
        show("مرکز عملیات هتل", "Hotel operations center", page);
    }

    private void attendancePage() {
        currentPage = "attendance";
        JPanel page = panel();
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEADING));
        Runnable[] refreshAttendance = new Runnable[]{() -> {}};
        JComboBox<String> shift = new JComboBox<>(new String[]{"Morning (08:00–16:00)", "Evening (16:00–00:00)", "Night (00:00–08:00)"});
        JButton in = button("ثبت ورود", "Clock in", () -> {
            String value = (String) shift.getSelectedItem();
            AttendanceService.Shift selected = switch (shift.getSelectedIndex()) {
                case 1 -> new AttendanceService.Shift("Evening", LocalTime.of(16,0), LocalTime.of(23,59));
                case 2 -> new AttendanceService.Shift("Night", LocalTime.MIDNIGHT, LocalTime.of(8,0));
                default -> new AttendanceService.Shift("Morning", LocalTime.of(8,0), LocalTime.of(16,0));
            };
            try { attendance.clockIn(currentUser, selected); }
            catch (Exception ex) { error(ex.getMessage()); }
            refreshAttendance[0].run();
        });
        JButton out = button("ثبت خروج", "Clock out", () -> { try { attendance.clockOut(currentUser); } catch (Exception ex) { error(ex.getMessage()); } refreshAttendance[0].run(); });
        top.add(new JLabel(tr("شیفت", "Shift"))); top.add(shift); top.add(in); top.add(out);
        page.add(top, BorderLayout.NORTH);
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Username", "Date", "Shift", "Check in", "Check out", "Worked min", "Late min", "Overtime min"}, 0) { public boolean isCellEditable(int r,int c){return false;} };
        JTable grid = new JTable(model); grid.setAutoCreateRowSorter(true); page.add(new JScrollPane(grid), BorderLayout.CENTER);
        JButton export = button("خروجی CSV", "Export CSV", () -> {
            JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new java.io.File("attendance-" + YearMonth.now() + ".csv"));
            if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) try { Files.writeString(chooser.getSelectedFile().toPath(), AttendanceService.csv(attendance.forMonth(YearMonth.now(), requireAdminCheck() ? null : currentUser)), java.nio.charset.StandardCharsets.UTF_8); }
            catch (Exception ex) { error(ex.getMessage()); }
        });
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEADING)); bottom.add(export); page.add(bottom, BorderLayout.SOUTH);
        // Populate immediately; the table refreshes after each action through this local helper.
        Runnable update = () -> { model.setRowCount(0); List<AttendanceService.Entry> rows = attendance.forMonth(YearMonth.now(), requireAdminCheck() ? null : currentUser); for (AttendanceService.Entry e : rows) model.addRow(new Object[]{e.username(),e.date(),e.shift(),e.checkIn()==null?"":e.checkIn().toLocalTime(),e.checkOut()==null?"":e.checkOut().toLocalTime(),e.workedMinutes(),e.lateMinutes(),e.overtimeMinutes()}); };
        refreshAttendance[0] = update;
        update.run(); show("حضور و غیاب کارکنان", "Staff attendance", page);
    }
    private void inventoryPage() {
        currentPage = "inventory";
        if (!requireAdmin()) return;
        JPanel page = panel();
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JLabel summary = new JLabel();
        JButton addItem = button("تعریف کالا", "Add item", () -> {
            JTextField sku=new JTextField(10), name=new JTextField(14), category=new JTextField(10), unit=new JTextField("pcs",8), minimum=new JTextField("0",8), cost=new JTextField("0",8), supplier=new JTextField(12);
            JPanel form=new JPanel(new GridLayout(0,2,5,5));
            String[] labels={"SKU","Name","Category","Unit","Minimum stock","Unit cost","Supplier"}; JTextField[] fields={sku,name,category,unit,minimum,cost,supplier};
            for(int i=0;i<labels.length;i++){form.add(new JLabel(labels[i]));form.add(fields[i]);}
            if(JOptionPane.showConfirmDialog(frame,form,tr("تعریف / ویرایش کالا","Item details"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) try {
                inventory.saveItem(new InventoryService.Item(sku.getText(),name.getText(),category.getText(),unit.getText(),new java.math.BigDecimal(minimum.getText()),new java.math.BigDecimal(cost.getText()),supplier.getText())); refresh[0].run();
            } catch(Exception ex){error(ex.getMessage());}
        });
        JButton receipt = button("ورود کالا", "Receive stock", () -> movementDialog(true, () -> refresh[0].run()));
        JButton consume = button("ثبت مصرف", "Record consumption", () -> movementDialog(false, () -> refresh[0].run()));
        JButton movements = button("گردش انبار", "Movement history", () -> showMovements());
        JButton export = button("خروجی موجودی CSV", "Export stock CSV", () -> saveInventoryCsv(false));
        JButton exportMoves = button("خروجی گردش CSV", "Export movements CSV", () -> saveInventoryCsv(true));
        top.add(addItem);top.add(receipt);top.add(consume);top.add(movements);top.add(export);top.add(exportMoves);top.add(summary);page.add(top,BorderLayout.NORTH);
        DefaultTableModel model=new DefaultTableModel(new Object[]{"SKU","Name","Category","Unit","On hand","Minimum","Unit cost","Stock value","Supplier","Status"},0){public boolean isCellEditable(int r,int c){return false;}};
        JTable grid=new JTable(model);grid.setRowHeight(32);grid.setAutoCreateRowSorter(true);page.add(new JScrollPane(grid),BorderLayout.CENTER);
        refresh[0]=()->{model.setRowCount(0);for(InventoryService.Stock st:inventory.stock()){InventoryService.Item i=st.item();model.addRow(new Object[]{i.sku(),i.name(),i.category(),i.unit(),st.quantity(),i.minimumStock(),i.unitCost(),st.stockValue(),i.supplier(),st.lowStock()?tr("نیاز به تأمین","LOW STOCK"):tr("موجود","OK")});}summary.setText(tr("اقلام: "+inventory.items().size()+" | کمبود: "+inventory.lowStock().size()+" | ارزش: "+inventory.totalValue(),"Items: "+inventory.items().size()+" | Low stock: "+inventory.lowStock().size()+" | Value: "+inventory.totalValue()));};
        refresh[0].run();show("مدیریت انبار و موجودی","Inventory & stock management",page);
    }
    private final Runnable[] refresh = {() -> {}};
    private void movementDialog(boolean receiving,Runnable refreshAction) {
        if(inventory.items().isEmpty()){error(tr("ابتدا یک کالا تعریف کنید","Add an item first"));return;}
        JComboBox<String> sku=new JComboBox<>(inventory.items().stream().map(InventoryService.Item::sku).toArray(String[]::new));
        JTextField quantity=new JTextField("1",9), cost=new JTextField("0",9), supplier=new JTextField(12), reference=new JTextField(12), notes=new JTextField(14);
        JPanel form=new JPanel(new GridLayout(0,2,5,5));form.add(new JLabel("SKU"));form.add(sku);form.add(new JLabel("Quantity"));form.add(quantity);
        if(receiving){form.add(new JLabel("Unit cost"));form.add(cost);form.add(new JLabel("Supplier"));form.add(supplier);}form.add(new JLabel("Reference"));form.add(reference);form.add(new JLabel("Notes"));form.add(notes);
        if(JOptionPane.showConfirmDialog(frame,form,receiving?tr("ورود موجودی","Stock receipt"):tr("مصرف موجودی","Stock consumption"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) try {
            if(receiving) inventory.receive((String)sku.getSelectedItem(),new java.math.BigDecimal(quantity.getText()),new java.math.BigDecimal(cost.getText()),supplier.getText(),reference.getText(),notes.getText());
            else inventory.consume((String)sku.getSelectedItem(),new java.math.BigDecimal(quantity.getText()),reference.getText(),notes.getText());refreshAction.run();
        }catch(Exception ex){error(ex.getMessage());}
    }
    private void showMovements(){DefaultTableModel m=new DefaultTableModel(new Object[]{"ID","SKU","Type","Quantity","Unit cost","Date","Reference","Notes","Supplier"},0){public boolean isCellEditable(int r,int c){return false;}};for(InventoryService.Movement x:inventory.movements())m.addRow(new Object[]{x.id(),x.sku(),x.type(),x.quantity(),x.unitCost(),x.occurredAt(),x.reference(),x.notes(),x.supplier()});JTable t=new JTable(m);t.setAutoCreateRowSorter(true);JOptionPane.showMessageDialog(frame,new JScrollPane(t),tr("گردش انبار","Stock movements"),JOptionPane.PLAIN_MESSAGE);}
    private void saveInventoryCsv(boolean movement){JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File(movement?"inventory-movements.csv":"inventory-stock.csv"));if(chooser.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION)try{String data=movement?inventory.movementCsv():InventoryService.stockCsv(inventory.stock());Files.writeString(chooser.getSelectedFile().toPath(),"\uFEFF"+data,java.nio.charset.StandardCharsets.UTF_8);JOptionPane.showMessageDialog(frame,tr("فایل ذخیره شد","CSV exported"));}catch(Exception ex){error(ex.getMessage());}}

    private void restaurantPage() {
        currentPage = "restaurant";
        JPanel page = panel();
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JButton newOrder = button("ثبت سفارش", "New order", () -> restaurantOrderDialog());
        JButton menuButton = button("مدیریت منو", "Manage menu", () -> restaurantMenuDialog());
        JButton statusButton = button("تغییر وضعیت سفارش", "Update order status", () -> restaurantStatusDialog());
        JButton export = button("خروجی فروش CSV", "Export sales CSV", () -> {
            JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new File("restaurant-orders.csv"));
            if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) try {
                Files.writeString(chooser.getSelectedFile().toPath(), "\uFEFF" + restaurant.ordersCsv(), java.nio.charset.StandardCharsets.UTF_8);
                JOptionPane.showMessageDialog(frame, tr("گزارش ذخیره شد", "Report exported"));
            } catch (Exception ex) { error(ex.getMessage()); }
        });
        actions.add(newOrder); actions.add(menuButton); actions.add(statusButton); actions.add(export);
        JLabel summary = new JLabel(); actions.add(summary); page.add(actions, BorderLayout.NORTH);
        DefaultTableModel model = new DefaultTableModel(new Object[]{"ID", "Table", "Guest", "Created", "Status", "Items", "Total"}, 0) { public boolean isCellEditable(int r,int c){return false;} };
        JTable grid = new JTable(model); grid.setRowHeight(32); grid.setAutoCreateRowSorter(true); page.add(new JScrollPane(grid), BorderLayout.CENTER);
        Runnable reload = () -> { model.setRowCount(0); for (RestaurantService.Order o : restaurant.orders()) model.addRow(new Object[]{o.id(),o.table(),o.guest(),o.createdAt(),o.status(),o.lines().stream().map(l->l.name()+" x"+l.quantity()).reduce((a,b)->a+", "+b).orElse(""),o.total()}); summary.setText(tr("سفارش‌ها: "+restaurant.orders().size()+" | فروش ثبت‌شده: "+restaurant.salesTotal(),"Orders: "+restaurant.orders().size()+" | Recorded sales: "+restaurant.salesTotal())); };
        reload.run(); show("مدیریت رستوران و کافی‌شاپ", "Restaurant & café management", page);
    }
    private void restaurantMenuDialog() {
        if (!requireAdmin()) return;
        JTextField code=new JTextField(8), name=new JTextField(14), category=new JTextField("Food",10), price=new JTextField("0",8), stockSku=new JTextField(10), perServing=new JTextField("0",8);
        JCheckBox available=new JCheckBox(tr("قابل سفارش", "Available"),true);
        JPanel form=new JPanel(new GridLayout(0,2,6,6)); String[] labels={"Code","Name","Category","Price","Inventory SKU (optional)","Stock per serving"}; JTextField[] fields={code,name,category,price,stockSku,perServing}; for(int i=0;i<labels.length;i++){form.add(new JLabel(labels[i]));form.add(fields[i]);}form.add(new JLabel("Availability"));form.add(available);
        if(JOptionPane.showConfirmDialog(frame,form,tr("افزودن / ویرایش آیتم منو", "Add / edit menu item"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{restaurant.saveMenuItem(new RestaurantService.MenuItem(code.getText(),name.getText(),category.getText(),new java.math.BigDecimal(price.getText()),stockSku.getText(),new java.math.BigDecimal(perServing.getText()),available.isSelected()));openCurrentPage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void restaurantOrderDialog() {
        List<RestaurantService.MenuItem> menu=restaurant.menu().stream().filter(RestaurantService.MenuItem::available).toList(); if(menu.isEmpty()){error(tr("ابتدا منوی قابل سفارش تعریف کنید", "Add available menu items first"));return;}
        JTextField tableField=new JTextField("1",8), guestField=new JTextField(12); JPanel form=new JPanel(new GridLayout(0,2,5,5));form.add(new JLabel(tr("شماره میز", "Table")));form.add(tableField);form.add(new JLabel(tr("نام مهمان", "Guest name")));form.add(guestField);
        Map<RestaurantService.MenuItem,JSpinner> quantities=new LinkedHashMap<>(); for(RestaurantService.MenuItem item:menu){form.add(new JLabel(item.name()+" - "+item.price()));JSpinner spinner=new JSpinner(new SpinnerNumberModel(0,0,99,1));quantities.put(item,spinner);form.add(spinner);}
        if(JOptionPane.showConfirmDialog(frame,new JScrollPane(form),tr("ثبت سفارش رستوران", "Create restaurant order"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{Map<String,Integer> requested=new LinkedHashMap<>();quantities.forEach((item,sp)->{int q=(Integer)sp.getValue();if(q>0)requested.put(item.code(),q);});RestaurantService.Order o=restaurant.placeOrder(tableField.getText(),guestField.getText(),requested);JOptionPane.showMessageDialog(frame,tr("سفارش شماره "+o.id()+" ثبت شد. مبلغ: "+o.total(),"Order #"+o.id()+" created. Total: "+o.total()));openCurrentPage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void restaurantStatusDialog() {
        List<RestaurantService.Order> orders=restaurant.orders().stream().filter(o->o.status()!=RestaurantService.OrderStatus.PAID&&o.status()!=RestaurantService.OrderStatus.CANCELLED).toList();if(orders.isEmpty()){error(tr("سفارش بازی وجود ندارد", "No open orders"));return;}
        JComboBox<RestaurantService.Order> selected=new JComboBox<>(orders.toArray(RestaurantService.Order[]::new));selected.setRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean s,boolean f){super.getListCellRendererComponent(l,v,i,s,f);if(v instanceof RestaurantService.Order o)setText("#"+o.id()+" | "+o.table()+" | "+o.status()+" | "+o.total());return this;}});
        JComboBox<RestaurantService.OrderStatus> status=new JComboBox<>(RestaurantService.OrderStatus.values());JPanel form=new JPanel(new GridLayout(0,2,5,5));form.add(new JLabel("Order"));form.add(selected);form.add(new JLabel("New status"));form.add(status);
        if(JOptionPane.showConfirmDialog(frame,form,tr("وضعیت سفارش", "Order status"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{RestaurantService.Order o=(RestaurantService.Order)selected.getSelectedItem();restaurant.updateStatus(o.id(),(RestaurantService.OrderStatus)status.getSelectedItem());openCurrentPage();}catch(Exception ex){error(ex.getMessage());}
    }

    private void eventsPage() {
        currentPage = "events";
        if (!requireAdmin()) return;
        JPanel page = panel(); JPanel top = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JButton halls = button("تعریف سالن", "Manage halls", this::eventHallDialog);
        JButton reserve = button("رزرو رویداد", "New reservation", this::eventReservationDialog);
        JButton status = button("وضعیت رویداد", "Update status", this::eventStatusDialog);
        JButton export = button("خروجی CSV", "Export CSV", () -> { JFileChooser c=new JFileChooser();c.setSelectedFile(new File("hotel-events.csv"));if(c.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION)try{Files.writeString(c.getSelectedFile().toPath(),"\uFEFF"+events.csv(),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception ex){error(ex.getMessage());} });
        JLabel summary=new JLabel();top.add(halls);top.add(reserve);top.add(status);top.add(export);top.add(summary);page.add(top,BorderLayout.NORTH);
        DefaultTableModel model=new DefaultTableModel(new Object[]{"ID","Hall","Event","Organizer","Guests","Start","End","Status","Hall cost","Catering","Other","Total"},0){public boolean isCellEditable(int r,int c){return false;}};
        JTable grid=new JTable(model);grid.setAutoCreateRowSorter(true);grid.setRowHeight(27);page.add(new JScrollPane(grid),BorderLayout.CENTER);
        for(EventService.Event e:events.events())model.addRow(new Object[]{e.id(),e.hallCode(),e.title(),e.organizer(),e.attendees(),e.start(),e.end(),e.status(),events.hallCost(e),e.cateringCost(),e.otherCost(),events.totalCost(e)});
        summary.setText(tr("رویدادها: "+events.events().size()+" | برآورد کل: "+events.revenueEstimate(),"Events: "+events.events().size()+" | Estimated total: "+events.revenueEstimate()));
        show("مدیریت سالن‌ها و رویدادها","Conference halls & events",page);
    }
    private void eventHallDialog() {
        JTextField code=new JTextField(8),name=new JTextField(14),capacity=new JTextField("50",8),rate=new JTextField("0",8),equipment=new JTextField(18);
        JPanel f=new JPanel(new GridLayout(0,2,5,5));String[] labels={"Hall code","Hall name","Capacity","Hourly rate","Equipment (comma separated)"};JTextField[] fields={code,name,capacity,rate,equipment};for(int i=0;i<labels.length;i++){f.add(new JLabel(labels[i]));f.add(fields[i]);}
        if(JOptionPane.showConfirmDialog(frame,f,tr("تعریف سالن کنفرانس","Conference hall details"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{events.saveHall(new EventService.Hall(code.getText(),name.getText(),Integer.parseInt(capacity.getText()),new java.math.BigDecimal(rate.getText()),equipment.getText()));openCurrentPage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void eventReservationDialog() {
        if(events.halls().isEmpty()){error(tr("ابتدا یک سالن تعریف کنید","Define a hall first"));return;}
        JComboBox<EventService.Hall> hall=new JComboBox<>(events.halls().toArray(EventService.Hall[]::new));hall.setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean s,boolean f){super.getListCellRendererComponent(l,v,i,s,f);if(v instanceof EventService.Hall h)setText(h.name()+" ("+h.capacity()+") - "+h.hourlyRate()+"/h");return this;}});
        JTextField title=new JTextField(14),organizer=new JTextField(12),attendees=new JTextField("20",6),start=new JTextField(LocalDateTime.now().plusDays(1).withMinute(0).withSecond(0).withNano(0).toString(),18),end=new JTextField(LocalDateTime.now().plusDays(1).plusHours(2).withMinute(0).withSecond(0).withNano(0).toString(),18),catering=new JTextField("0",8),other=new JTextField("0",8),notes=new JTextField(18);
        JPanel f=new JPanel(new GridLayout(0,2,5,5));Object[][] rows={{"Hall",hall},{"Event title",title},{"Organizer",organizer},{"Attendees",attendees},{"Start (yyyy-MM-ddTHH:mm)",start},{"End (yyyy-MM-ddTHH:mm)",end},{"Catering cost",catering},{"Other cost",other},{"Notes",notes}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}
        if(JOptionPane.showConfirmDialog(frame,f,tr("رزرو سالن و ثبت رویداد","Reserve hall and create event"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{EventService.Hall h=(EventService.Hall)hall.getSelectedItem();EventService.Event e=events.reserve(h.code(),title.getText(),organizer.getText(),Integer.parseInt(attendees.getText()),LocalDateTime.parse(start.getText()),LocalDateTime.parse(end.getText()),new java.math.BigDecimal(catering.getText()),new java.math.BigDecimal(other.getText()),notes.getText());JOptionPane.showMessageDialog(frame,tr("رزرو ثبت شد. هزینه برآوردی: "+events.totalCost(e),"Reservation created. Estimated cost: "+events.totalCost(e)));openCurrentPage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void eventStatusDialog() {
        List<EventService.Event> active=events.events().stream().filter(e->e.status()!=EventService.Status.CANCELLED&&e.status()!=EventService.Status.COMPLETED).toList();if(active.isEmpty()){error(tr("رویداد بازی وجود ندارد","No active events"));return;}
        JComboBox<EventService.Event> selected=new JComboBox<>(active.toArray(EventService.Event[]::new));selected.setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean s,boolean f){super.getListCellRendererComponent(l,v,i,s,f);if(v instanceof EventService.Event e)setText("#"+e.id()+" | "+e.title()+" | "+e.start());return this;}});
        JComboBox<EventService.Status> status=new JComboBox<>(EventService.Status.values());JPanel f=new JPanel(new GridLayout(0,2,5,5));f.add(new JLabel("Event"));f.add(selected);f.add(new JLabel("Status"));f.add(status);
        if(JOptionPane.showConfirmDialog(frame,f,tr("به‌روزرسانی وضعیت رویداد","Update event status"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{events.updateStatus(((EventService.Event)selected.getSelectedItem()).id(),(EventService.Status)status.getSelectedItem());openCurrentPage();}catch(Exception ex){error(ex.getMessage());}
    }

    private void spaPage() {
        currentPage = "spa";
        if (!requireAdmin()) return;
        JPanel page=panel(); JPanel top=new JPanel(new FlowLayout(FlowLayout.LEADING));
        JButton facility=button("تعریف مرکز / امکانات","Manage facilities",this::spaFacilityDialog);
        JButton service=button("تعریف خدمت","Manage services",this::spaOfferingDialog);
        JButton book=button("رزرو خدمت","New appointment",this::spaBookingDialog);
        JButton status=button("وضعیت رزرو","Update status",this::spaStatusDialog);
        JButton export=button("خروجی CSV","Export CSV",()->{JFileChooser c=new JFileChooser();c.setSelectedFile(new File("spa-appointments.csv"));if(c.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION)try{Files.writeString(c.getSelectedFile().toPath(),"\uFEFF"+spa.csv(),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception ex){error(ex.getMessage());}});
        JLabel summary=new JLabel();top.add(facility);top.add(service);top.add(book);top.add(status);top.add(export);top.add(summary);page.add(top,BorderLayout.NORTH);
        DefaultTableModel model=new DefaultTableModel(new Object[]{"ID","Facility","Service","Guest","Staff","Start","End","Guests","Status","Total"},0){public boolean isCellEditable(int r,int c){return false;}};
        JTable grid=new JTable(model);grid.setAutoCreateRowSorter(true);grid.setRowHeight(27);page.add(new JScrollPane(grid),BorderLayout.CENTER);
        for(SpaService.Appointment a:spa.appointments())model.addRow(new Object[]{a.id(),a.facilityCode(),a.serviceCode(),a.guest(),a.staff(),a.start(),a.end(),a.participants(),a.status(),spa.total(a)});
        summary.setText(tr("رزروها: "+spa.appointments().size()+" | برآورد درآمد: "+spa.revenueEstimate(),"Appointments: "+spa.appointments().size()+" | Estimated revenue: "+spa.revenueEstimate()));
        show("مدیریت اسپا و تفریحات","Spa & Leisure Management",page);
    }
    private void spaFacilityDialog(){
        JTextField code=new JTextField(8),name=new JTextField(14),capacity=new JTextField("1",6),description=new JTextField(18);
        JPanel f=new JPanel(new GridLayout(0,2,5,5));Object[][] rows={{"Code",code},{"Name",name},{"Capacity",capacity},{"Description",description}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}
        if(JOptionPane.showConfirmDialog(frame,f,tr("تعریف مرکز یا امکانات","Facility details"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{spa.saveFacility(new SpaService.Facility(code.getText(),name.getText(),Integer.parseInt(capacity.getText()),description.getText()));openCurrentPage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void spaOfferingDialog(){
        JTextField code=new JTextField(8),name=new JTextField(14),category=new JTextField("Spa",10),duration=new JTextField("60",6),price=new JTextField("0",8);JCheckBox active=new JCheckBox("Active",true);
        JPanel f=new JPanel(new GridLayout(0,2,5,5));Object[][] rows={{"Code",code},{"Name",name},{"Category",category},{"Duration (minutes)",duration},{"Price per participant",price},{"Available",active}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}
        if(JOptionPane.showConfirmDialog(frame,f,tr("تعریف خدمت اسپا یا تفریحی","Service details"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{spa.saveOffering(new SpaService.Offering(code.getText(),name.getText(),category.getText(),Integer.parseInt(duration.getText()),new java.math.BigDecimal(price.getText()),active.isSelected()));openCurrentPage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void spaBookingDialog(){
        if(spa.facilities().isEmpty()||spa.offerings().stream().noneMatch(SpaService.Offering::active)){error(tr("ابتدا مرکز و خدمت فعال تعریف کنید","Define a facility and active service first"));return;}
        JComboBox<SpaService.Facility> facility=new JComboBox<>(spa.facilities().toArray(SpaService.Facility[]::new));JComboBox<SpaService.Offering> offering=new JComboBox<>(spa.offerings().stream().filter(SpaService.Offering::active).toArray(SpaService.Offering[]::new));
        JTextField guest=new JTextField(14),staff=new JTextField(12),start=new JTextField(LocalDateTime.now().plusDays(1).withMinute(0).withSecond(0).withNano(0).toString(),18),participants=new JTextField("1",5),extra=new JTextField("0",8),notes=new JTextField(16);
        JPanel f=new JPanel(new GridLayout(0,2,5,5));Object[][] rows={{"Facility",facility},{"Service",offering},{"Guest",guest},{"Staff / therapist",staff},{"Start (yyyy-MM-ddTHH:mm)",start},{"Participants",participants},{"Extra charge",extra},{"Notes",notes}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}
        if(JOptionPane.showConfirmDialog(frame,f,tr("رزرو خدمت","Book spa or leisure service"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{SpaService.Facility x=(SpaService.Facility)facility.getSelectedItem();SpaService.Offering o=(SpaService.Offering)offering.getSelectedItem();SpaService.Appointment a=spa.book(x.code(),o.code(),guest.getText(),staff.getText(),LocalDateTime.parse(start.getText()),Integer.parseInt(participants.getText()),new java.math.BigDecimal(extra.getText()),notes.getText());JOptionPane.showMessageDialog(frame,tr("رزرو ثبت شد؛ مبلغ: "+spa.total(a),"Appointment booked; total: "+spa.total(a)));openCurrentPage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void spaStatusDialog(){
        List<SpaService.Appointment> active=spa.appointments().stream().filter(a->a.status()!=SpaService.Status.CANCELLED&&a.status()!=SpaService.Status.COMPLETED).toList();if(active.isEmpty()){error(tr("رزرو فعالی وجود ندارد","No active appointments"));return;}
        JComboBox<SpaService.Appointment> selected=new JComboBox<>(active.toArray(SpaService.Appointment[]::new));selected.setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean s,boolean f){super.getListCellRendererComponent(l,v,i,s,f);if(v instanceof SpaService.Appointment a)setText("#"+a.id()+" | "+a.guest()+" | "+a.start());return this;}});JComboBox<SpaService.Status> status=new JComboBox<>(SpaService.Status.values());JPanel f=new JPanel(new GridLayout(0,2,5,5));f.add(new JLabel("Appointment"));f.add(selected);f.add(new JLabel("Status"));f.add(status);
        if(JOptionPane.showConfirmDialog(frame,f,tr("به‌روزرسانی وضعیت رزرو","Update appointment status"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{spa.updateStatus(((SpaService.Appointment)selected.getSelectedItem()).id(),(SpaService.Status)status.getSelectedItem());openCurrentPage();}catch(Exception ex){error(ex.getMessage());}
    }

    private void guestJourneyPage() {
        currentPage = "guest-journey";
        JPanel page=panel(); JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEADING));
        actions.add(button("ثبت هزینه / Add charge","Add charge",this::journeyAddCharge));
        actions.add(button("ثبت پرداخت / Settle","Settle",this::journeySettle));
        actions.add(button("تغییر اتاق / Move room","Move room",this::journeyMoveRoom));
        actions.add(button("تمدید اقامت / Extend stay","Extend stay",this::journeyExtendStay));
        actions.add(button("تسویه و خروج / Check out","Check out",this::journeyCheckout));
        actions.add(button("گردش حساب / Folio","Folio",this::journeyShowFolio));
        actions.add(button("خروجی CSV / Export","Export CSV",()->{JFileChooser c=new JFileChooser();c.setSelectedFile(new File("guest-folios.csv"));if(c.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION)try{Files.writeString(c.getSelectedFile().toPath(),"\uFEFF"+guestJourney.csv(),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception ex){error(ex.getMessage());}}));
        page.add(actions,BorderLayout.NORTH);
        DefaultTableModel model=new DefaultTableModel(new Object[]{"Booking","Room","Guest","Check-in","Check-out","Status","Accommodation","Extras","Paid","Balance"},0){public boolean isCellEditable(int r,int c){return false;}};
        JTable grid=new JTable(model);grid.setAutoCreateRowSorter(true);grid.setRowHeight(27);
        for(Booking b:repository.bookings){Room room=repository.rooms.stream().filter(r->r.id.equals(b.roomId)).findFirst().orElse(null);if(room==null)continue;Guest guest=repository.guests.stream().filter(g->g.id.equals(b.guestId)).findFirst().orElse(null);java.math.BigDecimal accommodation=journeyAccommodation(b,room);model.addRow(new Object[]{b.id,b.roomId,guest==null?b.guestId:guest.name,b.start,b.end,b.status,accommodation,guestJourney.chargesTotal(b.id),guestJourney.paidTotal(b.id),guestJourney.balance(b.id,accommodation)});}
        page.add(new JScrollPane(grid),BorderLayout.CENTER);
        page.add(new JLabel(tr("حساب مهمان شامل هزینه اقامت، خدمات جانبی و پرداخت‌هاست. خروج نهایی تنها پس از تسویه مانده حساب انجام می‌شود.","Guest folio combines accommodation, extras and settlements. Final checkout requires a zero balance.")),BorderLayout.SOUTH);
        show("پذیرش و مسیر اقامت مهمان","Front Desk & Guest Journey",page);
    }
    private Booking journeySelectedBooking(){List<Booking> eligible=repository.bookings.stream().filter(b->b.status.equals("CHECKED_IN")||b.status.equals("CONFIRMED")).toList();if(eligible.isEmpty()){error(tr("رزرو فعالی وجود ندارد","No active bookings"));return null;}JComboBox<Booking> pick=new JComboBox<>(eligible.toArray(Booking[]::new));pick.setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean s,boolean f){super.getListCellRendererComponent(l,v,i,s,f);if(v instanceof Booking b)setText(b.id+" | "+b.guestId+" | "+b.roomId+" | "+b.status);return this;}});if(JOptionPane.showConfirmDialog(frame,pick,tr("انتخاب رزرو","Select booking"),JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return null;return (Booking)pick.getSelectedItem();}
    private java.math.BigDecimal journeyAccommodation(Booking b,Room room){long nights=java.time.temporal.ChronoUnit.DAYS.between(b.start,b.end);return java.math.BigDecimal.valueOf(room.rate).multiply(java.math.BigDecimal.valueOf(nights)).setScale(2,java.math.RoundingMode.HALF_UP);}
    private void journeyAddCharge(){Booking b=journeySelectedBooking();if(b==null)return;JTextField category=new JTextField("Restaurant",12),description=new JTextField(18),amount=new JTextField("25",8);JPanel f=new JPanel(new GridLayout(0,2,5,5));Object[][] rows={{"Category",category},{"Description",description},{"Amount",amount}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}if(JOptionPane.showConfirmDialog(frame,f,tr("ثبت هزینه جانبی","Add guest charge"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{guestJourney.addCharge(b.id,category.getText(),description.getText(),new java.math.BigDecimal(amount.getText()));guestJourneyPage();}catch(Exception ex){error(ex.getMessage());}}
    private void journeySettle(){Booking b=journeySelectedBooking();if(b==null)return;Room room=repository.rooms.stream().filter(r->r.id.equals(b.roomId)).findFirst().orElse(null);if(room==null)return;java.math.BigDecimal due=guestJourney.balance(b.id,journeyAccommodation(b,room));if(due.signum()<=0){error(tr("مانده‌ای برای پرداخت وجود ندارد","No outstanding balance"));return;}JTextField amount=new JTextField(due.toPlainString(),10);JComboBox<String> method=new JComboBox<>(new String[]{"Cash","Card","Bank transfer"});JPanel f=new JPanel(new GridLayout(0,2,5,5));f.add(new JLabel("Outstanding"));f.add(new JLabel(due.toPlainString()));f.add(new JLabel("Payment amount"));f.add(amount);f.add(new JLabel("Method"));f.add(method);if(JOptionPane.showConfirmDialog(frame,f,tr("ثبت تسویه","Record settlement"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{guestJourney.settle(b.id,(String)method.getSelectedItem(),new java.math.BigDecimal(amount.getText()),journeyAccommodation(b,room));guestJourneyPage();}catch(Exception ex){error(ex.getMessage());}}
    private void journeyMoveRoom(){Booking b=journeySelectedBooking();if(b==null||!b.status.equals("CHECKED_IN")){error(tr("فقط مهمان پذیرش‌شده قابل جابه‌جایی است","Only checked-in guests can move rooms"));return;}List<Room> candidates=repository.rooms.stream().filter(r->!r.id.equals(b.roomId)&&housekeeping.status(r.id)!=HousekeepingService.Status.OUT_OF_SERVICE&&!repository.bookings.stream().anyMatch(x->x!=b&&x.roomId.equals(r.id)&&(x.status.equals("CONFIRMED")||x.status.equals("CHECKED_IN"))&&b.start.isBefore(x.end)&&b.end.isAfter(x.start))).toList();if(candidates.isEmpty()){error(tr("اتاق جایگزین موجود نیست","No alternative room is available"));return;}JComboBox<Room> room=new JComboBox<>(candidates.toArray(Room[]::new));room.setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean s,boolean f){super.getListCellRendererComponent(l,v,i,s,f);if(v instanceof Room r)setText(r.id+" | "+r.type+" | "+r.rate);return this;}});if(JOptionPane.showConfirmDialog(frame,room,tr("انتقال مهمان به اتاق دیگر","Move guest to another room"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){b.roomId=((Room)room.getSelectedItem()).id;repository.save();guestJourneyPage();}}
    private void journeyExtendStay(){Booking b=journeySelectedBooking();if(b==null)return;JTextField end=new JTextField(b.end.plusDays(1).toString(),12);if(JOptionPane.showConfirmDialog(frame,end,tr("تاریخ خروج جدید (yyyy-MM-dd)","New check-out date (yyyy-MM-dd)"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{LocalDate next=LocalDate.parse(end.getText().trim());if(!next.isAfter(b.end))throw new IllegalArgumentException("New checkout must be after current checkout");if(repository.bookings.stream().anyMatch(x->x!=b&&x.roomId.equals(b.roomId)&&(x.status.equals("CONFIRMED")||x.status.equals("CHECKED_IN"))&&b.end.isBefore(x.end)&&next.isAfter(x.start)))throw new IllegalStateException("Room is already booked during the extension");b.end=next;repository.save();guestJourneyPage();}catch(Exception ex){error(ex.getMessage());}}
    private void journeyCheckout(){Booking b=journeySelectedBooking();if(b==null)return;if(!b.status.equals("CHECKED_IN")){error(tr("ابتدا مهمان باید پذیرش شود","Guest must be checked in first"));return;}Room room=repository.rooms.stream().filter(r->r.id.equals(b.roomId)).findFirst().orElse(null);if(room==null)return;java.math.BigDecimal due=guestJourney.balance(b.id,journeyAccommodation(b,room));if(due.signum()>0){error(tr("ابتدا مانده حساب را تسویه کنید: ","Settle the outstanding balance first: ")+due);return;}if(JOptionPane.showConfirmDialog(frame,tr("خروج نهایی مهمان ثبت شود؟","Confirm final guest checkout?"),tr("تأیید خروج","Confirm checkout"),JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){b.status="CHECKED_OUT";repository.save();guestJourneyPage();}}
    private void journeyShowFolio(){Booking b=journeySelectedBooking();if(b==null)return;Room room=repository.rooms.stream().filter(r->r.id.equals(b.roomId)).findFirst().orElse(null);if(room==null)return;StringBuilder text=new StringBuilder("Booking: ").append(b.id).append("\nAccommodation: ").append(journeyAccommodation(b,room)).append("\n\nCharges:\n");for(GuestJourneyService.Charge c:guestJourney.charges(b.id))text.append(c.createdAt()).append(" | ").append(c.category()).append(" | ").append(c.description()).append(" | ").append(c.amount()).append('\n');text.append("\nPayments:\n");for(GuestJourneyService.Settlement s:guestJourney.settlements(b.id))text.append(s.createdAt()).append(" | ").append(s.method()).append(" | ").append(s.amount()).append('\n');text.append("\nTotal: ").append(guestJourney.total(b.id,journeyAccommodation(b,room))).append("\nPaid: ").append(guestJourney.paidTotal(b.id)).append("\nBalance: ").append(guestJourney.balance(b.id,journeyAccommodation(b,room)));JTextArea area=new JTextArea(text.toString(),20,60);area.setEditable(false);JOptionPane.showMessageDialog(frame,new JScrollPane(area),tr("گردش حساب مهمان","Guest folio"),JOptionPane.INFORMATION_MESSAGE);}

    private void channelManagerPage() {
        currentPage = "channel-manager";
        if (!requireAdmin()) return;
        JPanel page=panel(), top=new JPanel(new FlowLayout(FlowLayout.LEADING));
        JButton channel=button("افزودن کانال / Add channel","Add channel",this::channelAddDialog);
        JButton room=button("تعریف نوع اتاق / Room type","Room type",this::channelRoomTypeDialog);
        JButton rate=button("تعریف نرخ و ظرفیت / Set rate","Set rate",this::channelRateDialog);
        JButton booking=button("ورود رزرو آنلاین / Import booking","Import booking",this::channelBookingDialog);
        JButton status=button("تغییر وضعیت / Update status","Update status",this::channelStatusDialog);
        JButton export=button("خروجی CSV / Export","Export CSV",()->{JFileChooser c=new JFileChooser();c.setSelectedFile(new File("hotel-channel-bookings.csv"));if(c.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION)try{Files.writeString(c.getSelectedFile().toPath(),"\uFEFF"+channelManager.csv(),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception ex){error(ex.getMessage());}});
        for(JButton b:List.of(channel,room,rate,booking,status,export))top.add(b);page.add(top,BorderLayout.NORTH);
        DefaultTableModel model=new DefaultTableModel(new Object[]{"ID","Channel","External ref","Guest","Room type","Check-in","Check-out","Rooms","Rate","Gross","Status"},0){public boolean isCellEditable(int r,int c){return false;}};
        JTable grid=new JTable(model);grid.setAutoCreateRowSorter(true);grid.setRowHeight(27);page.add(new JScrollPane(grid),BorderLayout.CENTER);
        for(ChannelManagerService.ExternalBooking b:channelManager.bookings())model.addRow(new Object[]{b.id(),b.channel(),b.externalReference(),b.guestName(),b.roomType(),b.checkIn(),b.checkOut(),b.rooms(),b.nightlyRate(),b.gross(),b.status()});
        JLabel summary=new JLabel(tr("کانال‌ها: "+channelManager.channels().size()+" | رزروها: "+channelManager.bookings().size()+" | فروش ناخالص: "+channelManager.grossRevenue()+" | کمیسیون: "+channelManager.commission()+" | خالص: "+channelManager.netRevenue(),"Channels: "+channelManager.channels().size()+" | Bookings: "+channelManager.bookings().size()+" | Gross: "+channelManager.grossRevenue()+" | Commission: "+channelManager.commission()+" | Net: "+channelManager.netRevenue()));page.add(summary,BorderLayout.SOUTH);
        show("مدیریت کانال‌های رزرو","Reservation Channel Manager",page);
    }
    private void channelAddDialog(){JTextField id=new JTextField(10),name=new JTextField(16),commission=new JTextField("15",8);JCheckBox active=new JCheckBox("Active",true);JPanel f=new JPanel(new GridLayout(0,2,5,5));Object[][] rows={{"Channel ID",id},{"Channel name",name},{"Commission %",commission},{"Active",active}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}if(JOptionPane.showConfirmDialog(frame,f,tr("تعریف کانال فروش","Add booking channel"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{channelManager.saveChannel(new ChannelManagerService.Channel(id.getText(),name.getText(),new java.math.BigDecimal(commission.getText()),active.isSelected()));openCurrentPage();}catch(Exception ex){error(ex.getMessage());}}
    private void channelRoomTypeDialog(){JTextField code=new JTextField(10),name=new JTextField(16),count=new JTextField("10",8);JPanel f=new JPanel(new GridLayout(0,2,5,5));Object[][] rows={{"Room type code",code},{"Name",name},{"Physical rooms",count}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}if(JOptionPane.showConfirmDialog(frame,f,tr("تعریف نوع اتاق","Add room type"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{channelManager.saveRoomType(new ChannelManagerService.RoomType(code.getText(),name.getText(),Integer.parseInt(count.getText())));openCurrentPage();}catch(Exception ex){error(ex.getMessage());}}
    private void channelRateDialog(){if(channelManager.channels().isEmpty()||channelManager.roomTypes().isEmpty()){error(tr("ابتدا کانال و نوع اتاق تعریف کنید","Add a channel and room type first"));return;}JComboBox<ChannelManagerService.Channel> ch=new JComboBox<>(channelManager.channels().toArray(ChannelManagerService.Channel[]::new));JComboBox<ChannelManagerService.RoomType> rt=new JComboBox<>(channelManager.roomTypes().toArray(ChannelManagerService.RoomType[]::new));JTextField from=new JTextField(LocalDate.now().toString(),10),to=new JTextField(LocalDate.now().plusMonths(3).toString(),10),price=new JTextField("100",8),allotment=new JTextField("5",8);JPanel f=new JPanel(new GridLayout(0,2,5,5));Object[][] rows={{"Channel",ch},{"Room type",rt},{"From (yyyy-MM-dd)",from},{"To (yyyy-MM-dd)",to},{"Nightly rate",price},{"Channel allotment",allotment}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}if(JOptionPane.showConfirmDialog(frame,f,tr("نرخ و ظرفیت کانال","Channel rate and allotment"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{channelManager.saveRate(new ChannelManagerService.Rate(((ChannelManagerService.RoomType)rt.getSelectedItem()).code(),((ChannelManagerService.Channel)ch.getSelectedItem()).id(),LocalDate.parse(from.getText()),LocalDate.parse(to.getText()),new java.math.BigDecimal(price.getText()),Integer.parseInt(allotment.getText())));openCurrentPage();}catch(Exception ex){error(ex.getMessage());}}
    private void channelBookingDialog(){if(channelManager.channels().isEmpty()||channelManager.roomTypes().isEmpty()){error(tr("ابتدا کانال و نوع اتاق تعریف کنید","Add a channel and room type first"));return;}JComboBox<ChannelManagerService.Channel> ch=new JComboBox<>(channelManager.channels().toArray(ChannelManagerService.Channel[]::new));JComboBox<ChannelManagerService.RoomType> rt=new JComboBox<>(channelManager.roomTypes().toArray(ChannelManagerService.RoomType[]::new));JTextField ref=new JTextField(12),guest=new JTextField(16),in=new JTextField(LocalDate.now().plusDays(1).toString(),10),out=new JTextField(LocalDate.now().plusDays(2).toString(),10),rooms=new JTextField("1",5),price=new JTextField("100",8);JPanel f=new JPanel(new GridLayout(0,2,5,5));Object[][] rows={{"Channel",ch},{"Room type",rt},{"External reference",ref},{"Guest",guest},{"Check-in",in},{"Check-out",out},{"Rooms",rooms},{"Quoted nightly rate (blank = channel rate)",price}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}if(JOptionPane.showConfirmDialog(frame,f,tr("ثبت رزرو ورودی از کانال","Import external booking"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{String p=price.getText().strip();ChannelManagerService.ExternalBooking b=channelManager.importBooking(((ChannelManagerService.Channel)ch.getSelectedItem()).id(),ref.getText(),guest.getText(),((ChannelManagerService.RoomType)rt.getSelectedItem()).code(),LocalDate.parse(in.getText()),LocalDate.parse(out.getText()),Integer.parseInt(rooms.getText()),p.isEmpty()?null:new java.math.BigDecimal(p));JOptionPane.showMessageDialog(frame,tr("رزرو ثبت شد. مبلغ ناخالص: ","Booking imported. Gross: ")+b.gross());openCurrentPage();}catch(Exception ex){error(ex.getMessage());}}
    private void channelStatusDialog(){List<ChannelManagerService.ExternalBooking> list=channelManager.bookings();if(list.isEmpty()){error(tr("رزروی ثبت نشده است","No imported bookings"));return;}JComboBox<ChannelManagerService.ExternalBooking> b=new JComboBox<>(list.toArray(ChannelManagerService.ExternalBooking[]::new));JComboBox<ChannelManagerService.BookingStatus> st=new JComboBox<>(ChannelManagerService.BookingStatus.values());JPanel f=new JPanel(new GridLayout(0,2,5,5));f.add(new JLabel("Booking"));f.add(b);f.add(new JLabel("Status"));f.add(st);if(JOptionPane.showConfirmDialog(frame,f,tr("وضعیت رزرو","Booking status"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{channelManager.updateStatus(((ChannelManagerService.ExternalBooking)b.getSelectedItem()).id(),(ChannelManagerService.BookingStatus)st.getSelectedItem());openCurrentPage();}catch(Exception ex){error(ex.getMessage());}}

    private void revenueManagementPage() {
        currentPage = "revenue-management";
        if (!requireAdmin()) return;
        JPanel page=panel(), top=new JPanel(new FlowLayout(FlowLayout.LEADING));
        JTextField from=new JTextField(LocalDate.now().toString(),10), to=new JTextField(LocalDate.now().plusDays(13).toString(),10);
        JButton refresh=button("به‌روزرسانی / Refresh","Refresh",()->revenueManagementPage());
        JButton rule=button("تعریف نرخ پویا / Add rate rule","Add rate rule",this::revenueRuleDialog);
        JButton export=button("خروجی CSV / Export","Export CSV",()->revenueExport(from.getText(),to.getText()));
        top.add(new JLabel(tr("از تاریخ","From")));top.add(from);top.add(new JLabel(tr("تا تاریخ","To")));top.add(to);
        JButton apply=button("اعمال بازه / Apply","Apply range",()->revenueRender(page,from,to));top.add(apply);top.add(rule);top.add(export);top.add(refresh);page.add(top,BorderLayout.NORTH);
        revenueRender(page,from,to);show("مدیریت درآمد و قیمت‌گذاری پویا","Revenue Management & Dynamic Pricing",page);
    }
    private List<RevenueManagementService.Stay> revenueStays(){List<RevenueManagementService.Stay> out=new ArrayList<>();for(Booking b:repository.bookings){Room r=repository.rooms.stream().filter(x->x.id.equals(b.roomId)).findFirst().orElse(null);if(r!=null)try{out.add(new RevenueManagementService.Stay(b.id,b.roomId,r.type,b.start,b.end,b.status,java.math.BigDecimal.valueOf(r.rate)));}catch(Exception ignored){}}return out;}
    private void revenueRender(JPanel page,JTextField fromField,JTextField toField){try{LocalDate from=LocalDate.parse(fromField.getText().trim()),to=LocalDate.parse(toField.getText().trim());List<RevenueManagementService.DailyMetric> metrics=revenueManagement.metrics(from,to,Math.max(1,repository.rooms.size()),revenueStays());List<RevenueManagementService.Recommendation> recs=revenueManagement.recommendations(metrics);
        JPanel body=new JPanel(new BorderLayout(8,8));JPanel kpis=new JPanel(new GridLayout(1,4,8,8));java.math.BigDecimal revenue=metrics.stream().map(RevenueManagementService.DailyMetric::roomRevenue).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);double avgOcc=metrics.stream().mapToDouble(RevenueManagementService.DailyMetric::occupancyPercent).average().orElse(0);java.math.BigDecimal adr=metrics.stream().map(RevenueManagementService.DailyMetric::adr).filter(x->x.signum()>0).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);long days=metrics.stream().filter(x->x.adr().signum()>0).count();long soldNights=metrics.stream().mapToLong(RevenueManagementService.DailyMetric::roomsSold).sum();adr=soldNights==0?java.math.BigDecimal.ZERO:revenue.divide(java.math.BigDecimal.valueOf(soldNights),2,java.math.RoundingMode.HALF_UP);java.math.BigDecimal revpar=metrics.stream().map(RevenueManagementService.DailyMetric::revPar).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add).divide(java.math.BigDecimal.valueOf(Math.max(1,metrics.size())),2,java.math.RoundingMode.HALF_UP);
        kpis.add(kpi(tr("درآمد اتاق‌ها","Room revenue"),revenue.toPlainString()));kpis.add(kpi(tr("میانگین اشغال","Average occupancy"),String.format(Locale.ROOT,"%.1f%%",avgOcc)));kpis.add(kpi("ADR",adr.toPlainString()));kpis.add(kpi("RevPAR",revpar.toPlainString()));body.add(kpis,BorderLayout.NORTH);
        DefaultTableModel model=new DefaultTableModel(new Object[]{"Date","Available","Sold","Occupancy %","Revenue","ADR","RevPAR","Suggested rate","Action"},0){public boolean isCellEditable(int r,int c){return false;}};JTable grid=new JTable(model);grid.setAutoCreateRowSorter(true);grid.setRowHeight(26);for(int i=0;i<metrics.size();i++){var m=metrics.get(i);var r=recs.get(i);model.addRow(new Object[]{m.date(),m.availableRooms(),m.roomsSold(),String.format(Locale.ROOT,"%.1f",m.occupancyPercent()),m.roomRevenue(),m.adr(),m.revPar(),r.suggestedRate(),r.action()});}body.add(new JScrollPane(grid),BorderLayout.CENTER);
        DefaultTableModel rulesModel=new DefaultTableModel(new Object[]{"Room type","From","To","Weekday","Rate","Label"},0){public boolean isCellEditable(int r,int c){return false;}};for(var r:revenueManagement.rules())rulesModel.addRow(new Object[]{r.roomType(),r.from(),r.to(),r.weekday()==null?"Every day":r.weekday(),r.nightlyRate(),r.label()});JTable rulesTable=new JTable(rulesModel);rulesTable.setRowHeight(23);
        DefaultTableModel channelModel=new DefaultTableModel(new Object[]{"Channel","Bookings","Gross","Commission","Net"},0){public boolean isCellEditable(int r,int c){return false;}};for(ChannelManagerService.Channel ch:channelManager.channels()){List<ChannelManagerService.ExternalBooking> cb=channelManager.bookings().stream().filter(x->x.channel().equals(ch.id())&&x.status()!=ChannelManagerService.BookingStatus.CANCELLED).toList();java.math.BigDecimal gross=cb.stream().map(ChannelManagerService.ExternalBooking::gross).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);java.math.BigDecimal commission=gross.multiply(ch.commissionPercent()).divide(new java.math.BigDecimal("100"),2,java.math.RoundingMode.HALF_UP);channelModel.addRow(new Object[]{ch.name(),cb.size(),gross,commission,gross.subtract(commission)});}
        JTable channelTable=new JTable(channelModel);channelTable.setRowHeight(23);JPanel south=new JPanel(new GridLayout(1,2,8,4));JPanel ratesPanel=new JPanel(new BorderLayout());ratesPanel.add(new JLabel(tr("نرخ‌های تعریف‌شده","Configured rates")),BorderLayout.NORTH);ratesPanel.add(new JScrollPane(rulesTable),BorderLayout.CENTER);JPanel channelsPanel=new JPanel(new BorderLayout());channelsPanel.add(new JLabel(tr("مقایسه درآمد کانال‌ها (تجمیعی)","Channel revenue comparison (all imported bookings)")),BorderLayout.NORTH);channelsPanel.add(new JScrollPane(channelTable),BorderLayout.CENTER);south.add(ratesPanel);south.add(channelsPanel);south.setPreferredSize(new Dimension(700,190));body.add(south,BorderLayout.SOUTH);
        Component header=page.getComponent(0);page.removeAll();page.add(header,BorderLayout.NORTH);page.add(body,BorderLayout.CENTER);page.revalidate();page.repaint();
    }catch(Exception ex){error(tr("بازهٔ تاریخ یا داده‌ها معتبر نیست: ","Invalid date range or data: ")+ex.getMessage());}}
    private JPanel kpi(String title,String value){JPanel p=new JPanel(new BorderLayout(3,8));p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(220,228,237)),new EmptyBorder(12,12,12,12)));JLabel t=new JLabel(title);t.setForeground(new Color(80,95,115));JLabel v=new JLabel(value);v.setFont(v.getFont().deriveFont(Font.BOLD,19f));v.setForeground(TEAL);p.add(t,BorderLayout.NORTH);p.add(v,BorderLayout.CENTER);return p;}
    private void revenueRuleDialog(){Set<String> types=new TreeSet<>();types.add("*");for(Room r:repository.rooms)types.add(r.type);JComboBox<String> type=new JComboBox<>(types.toArray(String[]::new));JTextField from=new JTextField(LocalDate.now().toString(),10),to=new JTextField(LocalDate.now().plusMonths(3).toString(),10),rate=new JTextField("150",8),label=new JTextField(14);JComboBox<String> weekday=new JComboBox<>(new String[]{"Every day","MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"});JPanel f=new JPanel(new GridLayout(0,2,5,5));Object[][] rows={{"Room type (* = all)",type},{"From (yyyy-MM-dd)",from},{"To (yyyy-MM-dd)",to},{"Weekday",weekday},{"Nightly rate",rate},{"Label",label}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}if(JOptionPane.showConfirmDialog(frame,f,tr("تعریف نرخ فصلی/روزانه","Create seasonal/day rate"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{String day=(String)weekday.getSelectedItem();revenueManagement.saveRule(new RevenueManagementService.RateRule((String)type.getSelectedItem(),LocalDate.parse(from.getText()),LocalDate.parse(to.getText()),day.equals("Every day")?null:DayOfWeek.valueOf(day),new java.math.BigDecimal(rate.getText()),label.getText()));revenueManagementPage();}catch(Exception ex){error(ex.getMessage());}}
    private void revenueExport(String fromText,String toText){try{List<RevenueManagementService.DailyMetric> data=revenueManagement.metrics(LocalDate.parse(fromText),LocalDate.parse(toText),Math.max(1,repository.rooms.size()),revenueStays());JFileChooser c=new JFileChooser();c.setSelectedFile(new File("hotel-revenue-management.csv"));if(c.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION)Files.writeString(c.getSelectedFile().toPath(),"\uFEFF"+revenueManagement.csv(data),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception ex){error(ex.getMessage());}}

    private void crmPage() {
        currentPage = "crm";
        if (!requireAdmin()) return;
        JPanel page=panel(); JPanel top=new JPanel(new FlowLayout(FlowLayout.LEADING));
        JButton add=button("افزودن مشتری / Add guest","Add guest",this::crmGuestDialog);
        JButton points=button("ثبت امتیاز / Adjust points","Adjust points",this::crmPointsDialog);
        JButton feedback=button("ثبت بازخورد / Add feedback","Add feedback",this::crmFeedbackDialog);
        JButton status=button("وضعیت بازخورد / Update status","Update feedback",this::crmFeedbackStatusDialog);
        JButton export=button("خروجی CSV / Export","Export CSV",()->{JFileChooser c=new JFileChooser();c.setSelectedFile(new File("hotel-crm.csv"));if(c.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION)try{Files.writeString(c.getSelectedFile().toPath(),"\uFEFF"+crm.csv(),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception ex){error(ex.getMessage());}});
        for(JButton b:List.of(add,points,feedback,status,export))top.add(b);page.add(top,BorderLayout.NORTH);
        DefaultTableModel model=new DefaultTableModel(new Object[]{"Guest ID","Name","Email","Phone","VIP","Points","Tier","Notes"},0){public boolean isCellEditable(int r,int c){return false;}};
        JTable grid=new JTable(model);grid.setAutoCreateRowSorter(true);grid.setRowHeight(27);page.add(new JScrollPane(grid),BorderLayout.CENTER);
        for(CrmService.Guest g:crm.guests())model.addRow(new Object[]{g.id(),g.name(),g.email(),g.phone(),g.vip(),crm.points(g.id()),crm.tier(g.id()),g.notes()});
        JLabel summary=new JLabel(tr("مشتریان: "+crm.guests().size()+" | بازخوردها: "+crm.feedback().size()+" | میانگین امتیاز: "+String.format(Locale.ROOT,"%.2f",crm.averageRating()),"Guests: "+crm.guests().size()+" | Feedback: "+crm.feedback().size()+" | Average rating: "+String.format(Locale.ROOT,"%.2f",crm.averageRating())));
        page.add(summary,BorderLayout.SOUTH);show("مدیریت ارتباط با مشتریان و وفاداری","Hotel CRM & Loyalty",page);
    }
    private void campaignPage() {
        currentPage = "campaigns";
        if (!requireAdmin()) return;
        JPanel page=panel(), top=new JPanel(new FlowLayout(FlowLayout.LEADING));
        JButton create=button("تعریف کمپین / New campaign","New campaign",this::campaignCreateDialog);
        JButton redeem=button("اعمال کد تخفیف / Redeem coupon","Redeem coupon",this::campaignRedeemDialog);
        JButton export=button("خروجی CSV / Export","Export CSV",()->{JFileChooser c=new JFileChooser();c.setSelectedFile(new File("hotel-campaigns.csv"));if(c.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION)try{Files.writeString(c.getSelectedFile().toPath(),"\uFEFF"+campaigns.csv(),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception ex){error(ex.getMessage());}});
        top.add(create);top.add(redeem);top.add(export);page.add(top,BorderLayout.NORTH);
        DefaultTableModel model=new DefaultTableModel(new Object[]{"Code","Campaign","Type","Value","Max discount","Min spend","Valid from","Valid to","Uses","Limit","Min tier","Active"},0){public boolean isCellEditable(int r,int c){return false;}};
        JTable grid=new JTable(model);grid.setAutoCreateRowSorter(true);grid.setRowHeight(27);page.add(new JScrollPane(grid),BorderLayout.CENTER);
        for(CampaignService.Campaign c:campaigns.campaigns())model.addRow(new Object[]{c.code(),c.name(),c.type(),c.value(),c.maxDiscount(),c.minimumSpend(),c.startsOn(),c.endsOn(),campaigns.usage(c.code()),c.maxUses()==0?"Unlimited":c.maxUses(),c.minimumTier(),c.active()});
        JLabel summary=new JLabel(tr("کمپین‌ها: "+campaigns.campaigns().size()+" | دفعات استفاده: "+campaigns.redemptions().size()+" | مجموع تخفیف: "+campaigns.totalDiscounts(),"Campaigns: "+campaigns.campaigns().size()+" | Redemptions: "+campaigns.redemptions().size()+" | Discounts: "+campaigns.totalDiscounts()));page.add(summary,BorderLayout.SOUTH);
        show("مدیریت بازاریابی و کمپین‌ها","Hotel Marketing & Campaigns",page);
    }
    private void campaignCreateDialog(){
        JTextField code=new JTextField(10),name=new JTextField(16),value=new JTextField("10",8),cap=new JTextField("0",8),minimum=new JTextField("0",8),start=new JTextField(LocalDate.now().toString(),10),end=new JTextField(LocalDate.now().plusMonths(1).toString(),10),limit=new JTextField("0",8);
        JComboBox<CampaignService.DiscountType> type=new JComboBox<>(CampaignService.DiscountType.values());JComboBox<CrmService.Tier> tier=new JComboBox<>(CrmService.Tier.values());JCheckBox active=new JCheckBox("Active",true);
        JPanel f=new JPanel(new GridLayout(0,2,5,5));Object[][] rows={{"Coupon code",code},{"Campaign name",name},{"Discount type",type},{"Value (% or amount)",value},{"Maximum discount (0 = none)",cap},{"Minimum spend",minimum},{"Start (yyyy-MM-dd)",start},{"End (yyyy-MM-dd)",end},{"Max uses (0 = unlimited)",limit},{"Minimum loyalty tier",tier},{"Active",active}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}
        if(JOptionPane.showConfirmDialog(frame,f,tr("تعریف کمپین تخفیف","Create discount campaign"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{campaigns.save(new CampaignService.Campaign(code.getText(),name.getText(),(CampaignService.DiscountType)type.getSelectedItem(),new java.math.BigDecimal(value.getText()),new java.math.BigDecimal(cap.getText()),new java.math.BigDecimal(minimum.getText()),LocalDate.parse(start.getText()),LocalDate.parse(end.getText()),Integer.parseInt(limit.getText()),(CrmService.Tier)tier.getSelectedItem(),active.isSelected()));openCurrentPage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void campaignRedeemDialog(){String guest=crmGuestIdDialog();if(guest==null)return;JTextField code=new JTextField(12),amount=new JTextField("100",10);JPanel f=new JPanel(new GridLayout(0,2,5,5));f.add(new JLabel("Coupon code"));f.add(code);f.add(new JLabel("Order amount"));f.add(amount);if(JOptionPane.showConfirmDialog(frame,f,tr("اعمال کوپن روی سفارش","Apply coupon to order"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{CampaignService.Redemption r=campaigns.redeem(code.getText(),guest,new java.math.BigDecimal(amount.getText()),crm);JOptionPane.showMessageDialog(frame,tr("تخفیف محاسبه‌شده: ","Discount calculated: ")+r.discount());openCurrentPage();}catch(Exception ex){error(ex.getMessage());}}

    private void crmGuestDialog(){
        JTextField id=new JTextField(10),name=new JTextField(16),email=new JTextField(16),phone=new JTextField(12),notes=new JTextField(18);JCheckBox vip=new JCheckBox("VIP");
        JPanel f=new JPanel(new GridLayout(0,2,5,5));Object[][] rows={{"Guest ID",id},{"Name",name},{"Email",email},{"Phone",phone},{"Notes",notes},{"VIP",vip}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}
        if(JOptionPane.showConfirmDialog(frame,f,tr("پرونده مشتری","Guest profile"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{crm.saveGuest(new CrmService.Guest(id.getText(),name.getText(),email.getText(),phone.getText(),notes.getText(),vip.isSelected()));openCurrentPage();}catch(Exception ex){error(ex.getMessage());}
    }
    private String crmGuestIdDialog(){if(crm.guests().isEmpty()){error(tr("ابتدا مشتری تعریف کنید","Add a guest first"));return null;}JComboBox<CrmService.Guest> choice=new JComboBox<>(crm.guests().toArray(CrmService.Guest[]::new));choice.setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean s,boolean f){super.getListCellRendererComponent(l,v,i,s,f);if(v instanceof CrmService.Guest g)setText(g.id()+" | "+g.name());return this;}});if(JOptionPane.showConfirmDialog(frame,choice,tr("انتخاب مشتری","Select guest"),JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return null;return ((CrmService.Guest)choice.getSelectedItem()).id();}
    private void crmPointsDialog(){String id=crmGuestIdDialog();if(id==null)return;JTextField points=new JTextField("100",8),reason=new JTextField("Stay reward",16);JPanel f=new JPanel(new GridLayout(0,2,5,5));f.add(new JLabel("Points (+/-)"));f.add(points);f.add(new JLabel("Reason"));f.add(reason);if(JOptionPane.showConfirmDialog(frame,f,tr("ثبت امتیاز وفاداری","Loyalty points"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{crm.adjustPoints(id,Integer.parseInt(points.getText()),reason.getText());openCurrentPage();}catch(Exception ex){error(ex.getMessage());}}
    private void crmFeedbackDialog(){String id=crmGuestIdDialog();if(id==null)return;JComboBox<Integer> rating=new JComboBox<>(new Integer[]{5,4,3,2,1});JTextField comment=new JTextField(22);JPanel f=new JPanel(new GridLayout(0,2,5,5));f.add(new JLabel("Rating (1-5)"));f.add(rating);f.add(new JLabel("Comment"));f.add(comment);if(JOptionPane.showConfirmDialog(frame,f,tr("ثبت بازخورد مهمان","Guest feedback"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{crm.addFeedback(id,(Integer)rating.getSelectedItem(),comment.getText());openCurrentPage();}catch(Exception ex){error(ex.getMessage());}}
    private void crmFeedbackStatusDialog(){List<CrmService.Feedback> items=crm.feedback();if(items.isEmpty()){error(tr("بازخوردی وجود ندارد","No feedback available"));return;}JComboBox<CrmService.Feedback> item=new JComboBox<>(items.toArray(CrmService.Feedback[]::new));JComboBox<CrmService.FeedbackStatus> status=new JComboBox<>(CrmService.FeedbackStatus.values());JPanel f=new JPanel(new GridLayout(0,2,5,5));f.add(new JLabel("Feedback"));f.add(item);f.add(new JLabel("Status"));f.add(status);if(JOptionPane.showConfirmDialog(frame,f,tr("به‌روزرسانی بازخورد","Update feedback"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{crm.updateFeedback(((CrmService.Feedback)item.getSelectedItem()).id(),(CrmService.FeedbackStatus)status.getSelectedItem());openCurrentPage();}catch(Exception ex){error(ex.getMessage());}}

    private void parkingPage() {
        currentPage = "parking";
        if (!requireAdmin()) return;
        JPanel page=panel(); JPanel top=new JPanel(new FlowLayout(FlowLayout.LEADING));
        JButton slot=button("تعریف جایگاه / Add slot","Add parking slot",this::parkingSlotDialog);
        JButton driver=button("تعریف راننده / Add driver","Add driver",this::parkingDriverDialog);
        JButton checkin=button("ورود خودرو / Check in","Vehicle check-in",this::parkingCheckInDialog);
        JButton checkout=button("خروج خودرو / Check out","Vehicle check-out",this::parkingCheckOutDialog);
        JButton transfer=button("رزرو ترانسفر / Book transfer","Book transfer",this::parkingTransferDialog);
        JButton status=button("وضعیت ترانسفر / Status","Transfer status",this::parkingTransferStatusDialog);
        JButton export=button("خروجی CSV / Export","Export CSV",()->{JFileChooser c=new JFileChooser();c.setSelectedFile(new File("parking-transport.csv"));if(c.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION)try{Files.writeString(c.getSelectedFile().toPath(),"\uFEFF"+parking.csv(),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception ex){error(ex.getMessage());}});
        JLabel summary=new JLabel(); for(JButton b:List.of(slot,driver,checkin,checkout,transfer,status,export))top.add(b);top.add(summary);page.add(top,BorderLayout.NORTH);
        DefaultTableModel model=new DefaultTableModel(new Object[]{"Type","ID","Guest","Vehicle / Route","Slot / Driver","Date","End","Amount","Status"},0){public boolean isCellEditable(int r,int c){return false;}};
        JTable grid=new JTable(model);grid.setAutoCreateRowSorter(true);grid.setRowHeight(27);page.add(new JScrollPane(grid),BorderLayout.CENTER);
        for(ParkingService.Visit v:parking.visits())model.addRow(new Object[]{"PARKING",v.id(),v.guest(),v.plate(),v.slotCode(),v.entry(),v.exit(),v.fee(),v.status()});
        for(ParkingService.Transfer t:parking.transfers())model.addRow(new Object[]{"TRANSFER",t.id(),t.guest(),t.pickup()+" → "+t.destination(),t.driverCode(),t.pickupAt(),"",t.fare(),t.status()});
        long occupied=parking.slots().stream().filter(x->parking.slotStatus(x.code())==ParkingService.SlotStatus.OCCUPIED).count();
        summary.setText(tr("جایگاه‌ها: "+parking.slots().size()+" | اشغال: "+occupied+" | درآمد پارکینگ: "+parking.parkingRevenue()+" | ترانسفر: "+parking.transferRevenue(),"Slots: "+parking.slots().size()+" | Occupied: "+occupied+" | Parking revenue: "+parking.parkingRevenue()+" | Transfers: "+parking.transferRevenue()));
        show("مدیریت پارکینگ و حمل‌ونقل","Parking & Transportation",page);
    }
    private void parkingSlotDialog(){
        JTextField code=new JTextField(8),zone=new JTextField("A",8),desc=new JTextField(18);JCheckBox enabled=new JCheckBox("Enabled",true);
        JPanel f=new JPanel(new GridLayout(0,2,5,5));Object[][] rows={{"Slot code",code},{"Zone",zone},{"Description",desc},{"Enabled",enabled}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}
        if(JOptionPane.showConfirmDialog(frame,f,tr("تعریف جایگاه پارکینگ","Parking slot details"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{parking.saveSlot(new ParkingService.Slot(code.getText(),zone.getText(),desc.getText(),enabled.isSelected()));openCurrentPage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void parkingDriverDialog(){
        JTextField code=new JTextField(8),name=new JTextField(14),phone=new JTextField(12),vehicle=new JTextField(12),plate=new JTextField(10);JCheckBox active=new JCheckBox("Active",true);
        JPanel f=new JPanel(new GridLayout(0,2,5,5));Object[][] rows={{"Code",code},{"Name",name},{"Phone",phone},{"Vehicle",vehicle},{"Plate",plate},{"Active",active}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}
        if(JOptionPane.showConfirmDialog(frame,f,tr("تعریف راننده","Driver details"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{parking.saveDriver(new ParkingService.Driver(code.getText(),name.getText(),phone.getText(),vehicle.getText(),plate.getText(),active.isSelected()));openCurrentPage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void parkingCheckInDialog(){
        List<ParkingService.Slot> free=parking.slots().stream().filter(s->parking.slotStatus(s.code())==ParkingService.SlotStatus.AVAILABLE).toList();if(free.isEmpty()){error(tr("جایگاه آزاد وجود ندارد","No available parking slots"));return;}
        JComboBox<ParkingService.Slot> slot=new JComboBox<>(free.toArray(ParkingService.Slot[]::new));JTextField guest=new JTextField(14),plate=new JTextField(10),type=new JTextField("Car",10),fee=new JTextField("0",8);JPanel f=new JPanel(new GridLayout(0,2,5,5));Object[][] rows={{"Slot",slot},{"Guest",guest},{"Plate",plate},{"Vehicle type",type},{"Parking fee",fee}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}
        if(JOptionPane.showConfirmDialog(frame,f,tr("ثبت ورود خودرو","Vehicle check-in"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{parking.checkIn(((ParkingService.Slot)slot.getSelectedItem()).code(),guest.getText(),plate.getText(),type.getText(),new java.math.BigDecimal(fee.getText()));openCurrentPage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void parkingCheckOutDialog(){List<ParkingService.Visit> active=parking.visits().stream().filter(v->v.status()==ParkingService.VisitStatus.ACTIVE).toList();if(active.isEmpty()){error(tr("خودروی فعالی در پارکینگ نیست","No active parking visits"));return;}JComboBox<ParkingService.Visit> pick=new JComboBox<>(active.toArray(ParkingService.Visit[]::new));pick.setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean s,boolean f){super.getListCellRendererComponent(l,v,i,s,f);if(v instanceof ParkingService.Visit x)setText(x.plate()+" | "+x.guest()+" | "+x.slotCode());return this;}});if(JOptionPane.showConfirmDialog(frame,pick,tr("ثبت خروج خودرو","Vehicle check-out"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{parking.checkOut(((ParkingService.Visit)pick.getSelectedItem()).id());openCurrentPage();}catch(Exception ex){error(ex.getMessage());}}
    private void parkingTransferDialog(){
        JTextField guest=new JTextField(14),pickup=new JTextField("Hotel",12),destination=new JTextField(14),driver=new JTextField(10),at=new JTextField(LocalDateTime.now().plusDays(1).withMinute(0).withSecond(0).withNano(0).toString(),18),passengers=new JTextField("1",5),fare=new JTextField("0",8),notes=new JTextField(14);
        JPanel f=new JPanel(new GridLayout(0,2,5,5));Object[][] rows={{"Guest",guest},{"Pickup",pickup},{"Destination",destination},{"Driver code (optional)",driver},{"Pickup (yyyy-MM-ddTHH:mm)",at},{"Passengers",passengers},{"Fare",fare},{"Notes",notes}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}
        if(JOptionPane.showConfirmDialog(frame,f,tr("رزرو ترانسفر","Book hotel transfer"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{parking.bookTransfer(guest.getText(),pickup.getText(),destination.getText(),driver.getText(),LocalDateTime.parse(at.getText()),Integer.parseInt(passengers.getText()),new java.math.BigDecimal(fare.getText()),notes.getText());openCurrentPage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void parkingTransferStatusDialog(){List<ParkingService.Transfer> active=parking.transfers().stream().filter(t->t.status()!=ParkingService.TransferStatus.CANCELLED&&t.status()!=ParkingService.TransferStatus.COMPLETED).toList();if(active.isEmpty()){error(tr("ترانسفر بازی وجود ندارد","No active transfers"));return;}JComboBox<ParkingService.Transfer> pick=new JComboBox<>(active.toArray(ParkingService.Transfer[]::new));JComboBox<ParkingService.TransferStatus> status=new JComboBox<>(ParkingService.TransferStatus.values());JPanel f=new JPanel(new GridLayout(0,2,5,5));f.add(new JLabel("Transfer"));f.add(pick);f.add(new JLabel("Status"));f.add(status);if(JOptionPane.showConfirmDialog(frame,f,tr("وضعیت ترانسفر","Transfer status"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{parking.updateTransfer(((ParkingService.Transfer)pick.getSelectedItem()).id(),(ParkingService.TransferStatus)status.getSelectedItem());openCurrentPage();}catch(Exception ex){error(ex.getMessage());}}

    private void payrollPage() {
        currentPage = "payroll";
        if (!requireAdmin()) return;
        JPanel page = panel();
        YearMonth month = YearMonth.now();
        JLabel period = new JLabel(tr("دوره حقوق: " + month, "Payroll period: " + month));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JButton configure = button("تعریف / ویرایش حقوق", "Set / edit compensation", () -> {
            JTextField user = new JTextField(12), base = new JTextField("0", 10), allowance = new JTextField("0", 10), hourly = new JTextField("0", 10), lateRate = new JTextField("0", 10), multiplier = new JTextField("1.5", 10);
            JPanel form = new JPanel(new GridLayout(0,2,6,6));
            form.add(new JLabel("Username")); form.add(user); form.add(new JLabel("Base salary")); form.add(base);
            form.add(new JLabel("Allowances")); form.add(allowance); form.add(new JLabel("Hourly rate")); form.add(hourly);
            form.add(new JLabel("Late deduction / hour")); form.add(lateRate); form.add(new JLabel("Overtime multiplier")); form.add(multiplier);
            if (JOptionPane.showConfirmDialog(frame, form, tr("اطلاعات حقوق", "Compensation details"), JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                try { payroll.save(new PayrollService.Compensation(user.getText(), new java.math.BigDecimal(base.getText()), new java.math.BigDecimal(allowance.getText()), new java.math.BigDecimal(hourly.getText()), new java.math.BigDecimal(lateRate.getText()), new java.math.BigDecimal(multiplier.getText()))); error(tr("اطلاعات حقوق ذخیره شد", "Compensation saved")); }
                catch (Exception ex) { error(ex.getMessage()); }
            }
        });
        top.add(period); top.add(configure); page.add(top, BorderLayout.NORTH);
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Username","Base","Allowances","Overtime pay","Late deduction","Net pay","OT min","Late min"},0) { public boolean isCellEditable(int r,int c){return false;} };
        JTable grid = new JTable(model); grid.setAutoCreateRowSorter(true); page.add(new JScrollPane(grid), BorderLayout.CENTER);
        Runnable refresh = () -> { model.setRowCount(0); for (PayrollService.Compensation c : payroll.all()) { PayrollService.Payslip p = payroll.calculate(c.username(), month, attendance.forMonth(month, c.username())); model.addRow(new Object[]{p.username(),p.baseSalary(),p.allowances(),p.overtimePay(),p.lateDeduction(),p.netPay(),p.overtimeMinutes(),p.lateMinutes()}); } };
        JButton export = button("خروجی CSV", "Export payroll CSV", () -> { JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new java.io.File("payroll-" + month + ".csv")); if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) try { List<PayrollService.Payslip> rows = payroll.all().stream().map(c -> payroll.calculate(c.username(), month, attendance.forMonth(month, c.username()))).toList(); Files.writeString(chooser.getSelectedFile().toPath(), PayrollService.csv(rows), java.nio.charset.StandardCharsets.UTF_8); } catch (Exception ex) { error(ex.getMessage()); } });
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEADING)); bottom.add(export); page.add(bottom, BorderLayout.SOUTH); refresh.run(); show("حقوق و دستمزد", "Payroll management", page);
    }

    private boolean requireAdminCheck() { return "Admin".equalsIgnoreCase(currentRole); }

    private void staffPerformancePage() {
        if (!requireAdmin()) return;
        currentPage = "performance";
        JPanel page = panel();
        DefaultTableModel model = new DefaultTableModel(new String[]{tr("کاربر", "User"), tr("کل رویدادها", "Total events"), tr("ایجاد", "Created"), tr("تغییر وضعیت", "Status changes"), tr("آخرین فعالیت", "Last activity")}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        try {
            for (StaffPerformanceService.Summary item : new StaffPerformanceService(AuditService.defaultService()).summaries()) {
                model.addRow(new Object[]{item.actor(), item.total(), item.created(), item.statusChanges(), item.lastActivity()});
            }
        } catch (RuntimeException ex) { error(ex.getMessage()); return; }
        JTable grid = new JTable(model);
        grid.setAutoCreateRowSorter(true);
        grid.setRowHeight(30);
        page.add(new JScrollPane(grid), BorderLayout.CENTER);
        show("گزارش عملکرد کارکنان", "Staff activity report", page);
    }
    private void auditPage() {
        if (!requireAdmin()) return;
        currentPage = "audit";
        JPanel page = panel();
        JTextField from = new JTextField(10), through = new JTextField(10), actor = new JTextField(10), query = new JTextField(16);
        from.setToolTipText("YYYY-MM-DD"); through.setToolTipText("YYYY-MM-DD");
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 6)); filters.setOpaque(false);
        filters.add(new JLabel(tr("از تاریخ", "From (YYYY-MM-DD)"))); filters.add(from);
        filters.add(new JLabel(tr("تا تاریخ", "Through"))); filters.add(through);
        filters.add(new JLabel(tr("کاربر", "Actor"))); filters.add(actor);
        filters.add(new JLabel(tr("جستجو", "Search"))); filters.add(query);
        DefaultTableModel model = new DefaultTableModel(new String[]{tr("شناسه", "ID"),tr("زمان (UTC)","Time (UTC)"),tr("بخش","Area"),tr("عملیات","Action"),tr("شناسه رکورد","Record ID"),tr("جزئیات","Details"),tr("کاربر","Actor")},0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable grid = new JTable(model); grid.setAutoCreateRowSorter(true); grid.setRowHeight(29);
        JLabel count = new JLabel();
        List<AuditService.Event> allEvents;
        try { allEvents = AuditService.defaultService().recent(1000); }
        catch (RuntimeException ex) { error(ex.getMessage()); return; }
        Runnable refresh = () -> {
            try {
                LocalDate startDate = from.getText().isBlank() ? null : LocalDate.parse(from.getText().trim());
                LocalDate endDate = through.getText().isBlank() ? null : LocalDate.parse(through.getText().trim());
                List<AuditService.Event> filtered = AuditReportService.filter(allEvents,
                        new AuditReportService.Filter(startDate, endDate, actor.getText(), query.getText()));
                model.setRowCount(0);
                for (AuditService.Event event : filtered) model.addRow(new Object[]{event.id(),event.time(),event.area(),event.action(),event.recordId(),event.detail(),event.actor()});
                count.setText(tr("رویدادهای نمایش‌داده‌شده: ", "Events shown: ") + filtered.size());
            } catch (RuntimeException ex) { error(tr("تاریخ نامعتبر است؛ قالب YYYY-MM-DD را وارد کنید.", "Invalid date. Use YYYY-MM-DD.") + "\n" + ex.getMessage()); }
        };
        JButton apply = button("اعمال فیلتر", "Apply filters", refresh);
        JButton clear = button("پاک‌کردن", "Clear", () -> { from.setText(""); through.setText(""); actor.setText(""); query.setText(""); refresh.run(); });
        JButton export = button("خروجی CSV", "Export filtered CSV", () -> {
            try {
                LocalDate startDate = from.getText().isBlank() ? null : LocalDate.parse(from.getText().trim());
                LocalDate endDate = through.getText().isBlank() ? null : LocalDate.parse(through.getText().trim());
                List<AuditService.Event> filtered = AuditReportService.filter(allEvents,
                        new AuditReportService.Filter(startDate, endDate, actor.getText(), query.getText()));
                JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new File("hotelsphere-audit.csv"));
                if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
                try (java.io.BufferedWriter writer = Files.newBufferedWriter(chooser.getSelectedFile().toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                    writer.write("\uFEFF"); AuditReportService.writeCsv(filtered, writer);
                }
                JOptionPane.showMessageDialog(frame, tr("گزارش فیلترشده ذخیره شد", "Filtered audit report saved"));
            } catch (Exception ex) { error(ex.getMessage()); }
        });
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING)); actions.setOpaque(false);
        actions.add(apply); actions.add(clear); actions.add(export); actions.add(count);
        JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false); top.add(filters, BorderLayout.CENTER); top.add(actions, BorderLayout.SOUTH);
        page.add(top, BorderLayout.NORTH); page.add(new JScrollPane(grid), BorderLayout.CENTER);
        refresh.run();
        show("تاریخچه فعالیت‌ها", "Operational audit trail", page);
    }
    private final GuestRequestService guestRequests = new GuestRequestService();
    private void guestRequestsPage() {
        currentPage="requests";
        JPanel page=panel();
        DefaultTableModel model=new DefaultTableModel(new String[]{"ID",tr("اتاق","Room"),tr("دسته","Category"),tr("شرح","Description"),tr("مسئول","Assignee"),tr("اولویت","Priority"),tr("وضعیت","Status"),tr("ثبت","Created")},0) {
            @Override public boolean isCellEditable(int r,int c){return false;}
        };
        for(GuestRequestService.Request r:guestRequests.all())model.addRow(new Object[]{r.id(),r.roomId(),r.category(),r.description(),r.assignee(),r.priority(),r.status(),r.createdAt()});
        JTable grid=new JTable(model);grid.setRowHeight(32);grid.setAutoCreateRowSorter(true);
        page.add(new JScrollPane(grid),BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEADING));actions.setOpaque(false);
        actions.add(button("درخواست جدید","New request",()->{
            JTextField room=new JTextField(), description=new JTextField(), assignee=new JTextField();
            JComboBox<String> category=new JComboBox<>(new String[]{"Housekeeping","Maintenance","Room service","Other"});
            JComboBox<GuestRequestService.Priority> priority=new JComboBox<>(GuestRequestService.Priority.values());
            JPanel form=new JPanel(new GridLayout(5,2,8,8));
            form.add(new JLabel(tr("شناسه اتاق","Room ID")));form.add(room);
            form.add(new JLabel(tr("نوع درخواست","Category")));form.add(category);
            form.add(new JLabel(tr("شرح","Description")));form.add(description);
            form.add(new JLabel(tr("مسئول","Assignee")));form.add(assignee);
            form.add(new JLabel(tr("اولویت","Priority")));form.add(priority);
            if(JOptionPane.showConfirmDialog(frame,form,tr("درخواست جدید","New request"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) {
                try {
                    if(repository.rooms.stream().noneMatch(r->r.id.equals(room.getText().trim())))throw new IllegalArgumentException("Unknown room");
                    guestRequests.create(room.getText(),String.valueOf(category.getSelectedItem()),description.getText(),assignee.getText(),(GuestRequestService.Priority)priority.getSelectedItem());guestRequestsPage();
                }catch(Exception ex){error(ex.getMessage());}
            }
        }));
        actions.add(button("شروع","Start",()->updateGuestRequest(grid,GuestRequestService.Status.IN_PROGRESS)));
        actions.add(button("حل شد","Resolve",()->updateGuestRequest(grid,GuestRequestService.Status.RESOLVED)));
        page.add(actions,BorderLayout.SOUTH);show("درخواست‌های خدمات مهمانان","Guest service requests",page);
    }
    private void updateGuestRequest(JTable grid,GuestRequestService.Status next) {
        int row=grid.getSelectedRow();if(row<0){error(tr("یک درخواست انتخاب کنید","Select a request"));return;}
        try {guestRequests.changeStatus(String.valueOf(grid.getValueAt(grid.convertRowIndexToModel(row),0)),next);guestRequestsPage();}
        catch(Exception ex){error(ex.getMessage());}
    }
    private final HousekeepingTaskService tasks = new HousekeepingTaskService();
    private void tasksPage() {
        currentPage = "tasks";
        JPanel panel = panel();
        DefaultTableModel model = new DefaultTableModel(new String[]{"ID", tr("اتاق","Room"), tr("عنوان","Title"), tr("مسئول","Assignee"), tr("اولویت","Priority"), tr("وضعیت","Status")},0) {
            @Override public boolean isCellEditable(int r,int c) {return false;}
        };
        for (HousekeepingTaskService.Task task : tasks.all()) model.addRow(new Object[]{task.id(),task.roomId(),task.title(),task.assignee(),task.priority(),task.status()});
        JTable table = new JTable(model); table.setRowHeight(32);
        panel.add(new JScrollPane(table),BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING)); actions.setOpaque(false);
        actions.add(button("وظیفه جدید","New task",()->{
            JTextField room = new JTextField(), title = new JTextField(), assignee = new JTextField();
            JComboBox<HousekeepingTaskService.Priority> priority = new JComboBox<>(HousekeepingTaskService.Priority.values());
            JPanel form = new JPanel(new GridLayout(4,2,8,8));
            form.add(new JLabel("Room ID"));form.add(room);form.add(new JLabel("Task"));form.add(title);
            form.add(new JLabel("Assignee"));form.add(assignee);form.add(new JLabel("Priority"));form.add(priority);
            if(JOptionPane.showConfirmDialog(frame,form,tr("وظیفه جدید","New task"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) {
                try {
                    if(repository.rooms.stream().noneMatch(r->r.id.equals(room.getText().trim()))) throw new IllegalArgumentException("Unknown room");
                    tasks.create(room.getText(),title.getText(),assignee.getText(),(HousekeepingTaskService.Priority)priority.getSelectedItem()); tasksPage();
                } catch(Exception ex) {error(ex.getMessage());}
            }
        }));
        actions.add(button("شروع","Start",()->updateTask(table,HousekeepingTaskService.Status.IN_PROGRESS)));
        actions.add(button("تکمیل","Complete",()->updateTask(table,HousekeepingTaskService.Status.DONE)));
        panel.add(actions,BorderLayout.SOUTH); show("وظایف کارکنان","Staff tasks",panel);
    }
    private void updateTask(JTable table,HousekeepingTaskService.Status status) {
        int row=table.getSelectedRow();if(row<0){error("Select a task");return;}
        try {tasks.changeStatus(String.valueOf(table.getValueAt(table.convertRowIndexToModel(row),0)),status);tasksPage();}
        catch(Exception ex){error(ex.getMessage());}
    }
    private void housekeepingPage() {
        currentPage = "housekeeping";
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);
        DefaultTableModel model = new DefaultTableModel(
            new String[]{tr("اتاق", "Room"), tr("نوع", "Type"), tr("وضعیت نظافت", "Cleaning status"), tr("اشغال", "Occupancy")}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        for (Room room : repository.rooms) {
            model.addRow(new Object[]{room.id(), room.type(), housekeeping.status(room.id()).name(),
                repository.active(room.id()) ? tr("اشغال", "Occupied") : tr("آزاد", "Vacant")});
        }
        JTable table = new JTable(model);
        table.setRowHeight(32);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 8));
        actions.setOpaque(false);
        for (HousekeepingService.Status status : HousekeepingService.Status.values()) {
            JButton action = new JButton(status.name().replace('_', ' '));
            action.addActionListener(event -> {
                int selected = table.getSelectedRow();
                if (selected < 0) { error(tr("ابتدا اتاقی انتخاب کنید", "Select a room first")); return; }
                String roomId = table.getValueAt(selected, 0).toString();
                try { housekeeping.update(roomId, status); housekeepingPage(); }
                catch (RuntimeException ex) { error(ex.getMessage()); }
            });
            actions.add(action);
        }
        panel.add(actions, BorderLayout.SOUTH);
        show("خانه‌داری", "Housekeeping", panel);
    }

    private final void housekeepingWorkforcePage() {
        currentPage = "housekeeping-workforce";
        JPanel page=panel();
        LocalDate today=LocalDate.now();
        List<HousekeepingWorkforceService.Assignment> rows=housekeepingWorkforce.forDate(today);
        List<HousekeepingWorkforceService.ShiftSummary> summaries=housekeepingWorkforce.shiftSummary(today);
        JPanel summary=new JPanel(new GridLayout(1,3,10,8)); summary.setOpaque(false);
        for(var x:summaries){JPanel card=new JPanel(new BorderLayout(4,4));card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(220,228,238)),new EmptyBorder(10,12,10,12)));card.setBackground(Color.WHITE);card.add(new JLabel(x.shift().name()),BorderLayout.NORTH);JLabel value=new JLabel(x.completed()+" / "+x.assigned()+"   •   urgent: "+x.urgent());value.setFont(value.getFont().deriveFont(Font.BOLD,16f));card.add(value,BorderLayout.CENTER);summary.add(card);}
        page.add(summary,BorderLayout.NORTH);
        DefaultTableModel model=new DefaultTableModel(new String[]{"ID",tr("تاریخ","Date"),tr("شیفت","Shift"),tr("کارمند","Employee"),tr("اتاق","Room"),tr("نوع کار","Task"),tr("اولویت","Priority"),tr("وضعیت","Status"),tr("مدت (دقیقه)","Minutes")},0){public boolean isCellEditable(int r,int c){return false;}};
        for(var a:rows){long mins=a.startedAt()!=null&&a.completedAt()!=null?java.time.Duration.between(a.startedAt(),a.completedAt()).toMinutes():0;model.addRow(new Object[]{a.id(),a.workDate(),a.shift(),a.employee(),a.roomId(),a.type(),a.priority(),a.status(),mins});}
        JTable table=new JTable(model);table.setRowHeight(30);table.setAutoCreateRowSorter(true);page.add(new JScrollPane(table),BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEADING));actions.setOpaque(false);
        actions.add(button("تخصیص کار / Assign task","Assign task",this::housekeepingAssignmentDialog));
        actions.add(button("ساخت وظایف امروز / Generate today","Generate today",this::housekeepingGenerateToday));
        actions.add(button("شروع کار / Start","Start",()->housekeepingUpdateSelected(table,false)));
        actions.add(button("تکمیل کار / Complete","Complete",()->housekeepingUpdateSelected(table,true)));
        actions.add(button("بهره‌وری کارکنان / Productivity","Productivity",this::housekeepingProductivityDialog));
        actions.add(button("خروجی CSV / Export","Export CSV",this::housekeepingWorkforceExport));
        page.add(actions,BorderLayout.SOUTH);show("برنامه‌ریزی نیروی خانه‌داری","Housekeeping Workforce & Staff Scheduling",page);
    }
    private void housekeepingAssignmentDialog(){
        if(repository.rooms.isEmpty()){error("No rooms available");return;}
        JTextField employee=new JTextField(14),date=new JTextField(LocalDate.now().toString(),10),notes=new JTextField(18);
        JComboBox<Room> room=new JComboBox<>(repository.rooms.toArray(Room[]::new));
        JComboBox<HousekeepingWorkforceService.Shift> shift=new JComboBox<>(HousekeepingWorkforceService.Shift.values());
        JComboBox<HousekeepingWorkforceService.TaskType> type=new JComboBox<>(HousekeepingWorkforceService.TaskType.values());
        JComboBox<HousekeepingWorkforceService.Priority> priority=new JComboBox<>(HousekeepingWorkforceService.Priority.values());
        JPanel f=new JPanel(new GridLayout(0,2,7,7));Object[][] fields={{"Employee",employee},{"Date yyyy-MM-dd",date},{"Room",room},{"Shift",shift},{"Task type",type},{"Priority",priority},{"Notes",notes}};for(Object[] x:fields){f.add(new JLabel((String)x[0]));f.add((Component)x[1]);}
        if(JOptionPane.showConfirmDialog(frame,f,tr("تخصیص وظیفه خانه‌داری","Assign housekeeping task"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{Room r=(Room)room.getSelectedItem();housekeepingWorkforce.assign(LocalDate.parse(date.getText()),(HousekeepingWorkforceService.Shift)shift.getSelectedItem(),employee.getText(),r.id(),(HousekeepingWorkforceService.TaskType)type.getSelectedItem(),(HousekeepingWorkforceService.Priority)priority.getSelectedItem(),notes.getText());housekeepingWorkforcePage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void housekeepingGenerateToday(){
        LocalDate today=LocalDate.now();int created=0;
        for(Booking b:repository.bookings){if(!b.start.equals(today)&&!b.end.equals(today))continue;if(!b.status.equals("CONFIRMED")&&!b.status.equals("CHECKED_IN"))continue;
            boolean checkout=b.end.equals(today);HousekeepingWorkforceService.TaskType type=checkout?HousekeepingWorkforceService.TaskType.CHECKOUT_CLEAN:HousekeepingWorkforceService.TaskType.ARRIVAL_PREPARATION;
            boolean exists=housekeepingWorkforce.forDate(today).stream().anyMatch(a->a.roomId().equals(b.roomId)&&a.status()!=HousekeepingWorkforceService.Status.COMPLETED);
            if(exists)continue;try{housekeepingWorkforce.assign(today,HousekeepingWorkforceService.Shift.MORNING,"Unassigned",b.roomId,type,checkout?HousekeepingWorkforceService.Priority.URGENT:HousekeepingWorkforceService.Priority.HIGH,checkout?"Generated from today's check-out":"Generated from today's arrival");created++;}catch(IllegalStateException ignored){}
        }
        JOptionPane.showMessageDialog(frame,tr("تعداد وظایف ایجادشده: ","Assignments created: ")+created);housekeepingWorkforcePage();
    }
    private void housekeepingUpdateSelected(JTable table,boolean complete){int row=table.getSelectedRow();if(row<0){error("Select an assignment first");return;}String id=String.valueOf(table.getValueAt(table.convertRowIndexToModel(row),0));try{if(complete)housekeepingWorkforce.complete(id,LocalDateTime.now());else housekeepingWorkforce.start(id,LocalDateTime.now());housekeepingWorkforcePage();}catch(Exception ex){error(ex.getMessage());}}
    private void housekeepingProductivityDialog(){List<HousekeepingWorkforceService.EmployeeLoad> loads=housekeepingWorkforce.employeeLoads(LocalDate.now().minusDays(6),LocalDate.now());if(loads.isEmpty()){error("No assignments in the last 7 days");return;}StringBuilder b=new StringBuilder("Employee | Assigned | Completed | Completion % | Avg minutes\n\n");for(var x:loads)b.append(x.employee()).append(" | ").append(x.assigned()).append(" | ").append(x.completed()).append(" | ").append(String.format(Locale.ROOT,"%.1f",x.completionRate())).append("% | ").append(String.format(Locale.ROOT,"%.1f",x.averageMinutes())).append('\n');JOptionPane.showMessageDialog(frame,new JScrollPane(new JTextArea(b.toString(),12,55)),tr("بهره‌وری هفت روز اخیر","Last 7 days productivity"),JOptionPane.INFORMATION_MESSAGE);}
    private void housekeepingWorkforceExport(){JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File("housekeeping-workforce.csv"));if(chooser.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION)try{Files.writeString(chooser.getSelectedFile().toPath(),"\uFEFF"+housekeepingWorkforce.csv(),java.nio.charset.StandardCharsets.UTF_8);JOptionPane.showMessageDialog(frame,"CSV exported");}catch(IOException ex){error(ex.getMessage());}}


    private void customerExperiencePage() {
        currentPage="customer-experience";
        var m=customerExperience.metrics(); JPanel page=panel();
        JPanel cards=new JPanel(new GridLayout(1,4,10,8));cards.setOpaque(false);
        cards.add(kpi(tr("نظرسنجی‌ها","Responses"),String.valueOf(m.responses())));
        cards.add(kpi("CSAT",String.format(Locale.ROOT,"%.1f%%",m.csat())));
        cards.add(kpi("NPS",String.format(Locale.ROOT,"%.1f",m.nps())));
        cards.add(kpi(tr("شکایت‌های باز","Open complaints"),String.valueOf(m.openComplaints())));
        page.add(cards,BorderLayout.NORTH);
        JTabbedPane tabs=new JTabbedPane();
        DefaultTableModel fm=new DefaultTableModel(new Object[]{"ID",tr("رزرو","Booking"),tr("مهمان","Guest"),tr("تاریخ","Date"),"CSAT","NPS",tr("نظر","Comment")},0){public boolean isCellEditable(int r,int c){return false;}};
        for(var x:customerExperience.feedback())fm.addRow(new Object[]{x.id(),x.bookingId(),x.guestName(),x.submittedAt(),x.overall()+"/5",x.nps(),x.comment()});
        JTable ft=new JTable(fm);ft.setRowHeight(28);tabs.addTab(tr("نظرسنجی‌ها","Feedback"),new JScrollPane(ft));
        DefaultTableModel cm=new DefaultTableModel(new Object[]{"ID",tr("رزرو","Booking"),tr("مهمان","Guest"),tr("دسته","Category"),tr("موضوع","Subject"),tr("مسئول","Assigned"),tr("وضعیت","Status"),tr("آخرین تغییر","Updated")},0){public boolean isCellEditable(int r,int c){return false;}};
        for(var x:customerExperience.complaints())cm.addRow(new Object[]{x.id(),x.bookingId(),x.guestName(),x.category(),x.subject(),x.assignedTo(),x.status(),x.updatedAt()});
        JTable ct=new JTable(cm);ct.setRowHeight(28);tabs.addTab(tr("شکایت‌ها و پیگیری","Complaints"),new JScrollPane(ct));page.add(tabs,BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEADING));actions.setOpaque(false);
        actions.add(button("ثبت نظرسنجی / New feedback","New feedback",this::customerFeedbackDialog));
        actions.add(button("ثبت شکایت / New complaint","New complaint",this::customerComplaintDialog));
        actions.add(button("به‌روزرسانی شکایت / Update complaint","Update complaint",this::customerComplaintUpdate));
        actions.add(button("خروجی CSV / Export","Export CSV",this::customerExperienceExport));page.add(actions,BorderLayout.SOUTH);
        show("مدیریت رضایت و تجربه مهمان","Guest Experience & Satisfaction",page);
    }
    private void customerFeedbackDialog(){
        JTextField booking=new JTextField(14),guest=new JTextField(14),comment=new JTextField(22);
        JSpinner clean=new JSpinner(new SpinnerNumberModel(5,1,5,1)),reception=new JSpinner(new SpinnerNumberModel(5,1,5,1)),restaurantRate=new JSpinner(new SpinnerNumberModel(5,1,5,1)),facilities=new JSpinner(new SpinnerNumberModel(5,1,5,1)),overall=new JSpinner(new SpinnerNumberModel(5,1,5,1)),nps=new JSpinner(new SpinnerNumberModel(10,0,10,1));
        JPanel f=new JPanel(new GridLayout(0,2,6,6));Object[][] rows={{"Booking ID",booking},{"Guest",guest},{"Cleanliness 1-5",clean},{"Reception 1-5",reception},{"Restaurant 1-5",restaurantRate},{"Facilities 1-5",facilities},{"Overall 1-5",overall},{"NPS 0-10",nps},{"Comment",comment}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}
        if(JOptionPane.showConfirmDialog(frame,f,tr("ثبت بازخورد مهمان","Submit guest feedback"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{customerExperience.submitFeedback(booking.getText(),guest.getText(),(int)clean.getValue(),(int)reception.getValue(),(int)restaurantRate.getValue(),(int)facilities.getValue(),(int)overall.getValue(),(int)nps.getValue(),comment.getText(),LocalDateTime.now());customerExperiencePage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void customerComplaintDialog(){
        JTextField booking=new JTextField(12),guest=new JTextField(12),category=new JTextField("General",12),subject=new JTextField(18),assigned=new JTextField(12),description=new JTextField(22);
        JPanel f=new JPanel(new GridLayout(0,2,6,6));Object[][] rows={{"Booking ID",booking},{"Guest",guest},{"Category",category},{"Subject",subject},{"Assigned to",assigned},{"Description",description}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}
        if(JOptionPane.showConfirmDialog(frame,f,tr("ثبت شکایت مهمان","Register guest complaint"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{customerExperience.createComplaint(booking.getText(),guest.getText(),category.getText(),subject.getText(),description.getText(),assigned.getText(),LocalDateTime.now());customerExperiencePage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void customerComplaintUpdate(){List<CustomerExperienceService.Complaint> all=customerExperience.complaints();if(all.isEmpty()){error("No complaints");return;}JComboBox<CustomerExperienceService.Complaint> pick=new JComboBox<>(all.toArray(CustomerExperienceService.Complaint[]::new));pick.setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> l,Object v,int i,boolean s,boolean f){super.getListCellRendererComponent(l,v,i,s,f);if(v instanceof CustomerExperienceService.Complaint c)setText(c.subject()+" | "+c.status()+" | "+c.guestName());return this;}});JComboBox<CustomerExperienceService.ComplaintStatus> status=new JComboBox<>(CustomerExperienceService.ComplaintStatus.values());JTextField assigned=new JTextField(12),resolution=new JTextField(18);JPanel f=new JPanel(new GridLayout(0,2,6,6));for(Object[] r:new Object[][]{{"Complaint",pick},{"Status",status},{"Assigned to",assigned},{"Resolution",resolution}}){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}if(JOptionPane.showConfirmDialog(frame,f,"Update complaint",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{customerExperience.updateComplaint(((CustomerExperienceService.Complaint)pick.getSelectedItem()).id(),(CustomerExperienceService.ComplaintStatus)status.getSelectedItem(),assigned.getText(),resolution.getText(),LocalDateTime.now());customerExperiencePage();}catch(Exception ex){error(ex.getMessage());}}
    private void customerExperienceExport(){JFileChooser c=new JFileChooser();c.setSelectedFile(new File("guest-experience.csv"));if(c.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION)try{String data="FEEDBACK\n"+customerExperience.feedbackCsv()+"\nCOMPLAINTS\n"+customerExperience.complaintsCsv();Files.writeString(c.getSelectedFile().toPath(),"\uFEFF"+data,java.nio.charset.StandardCharsets.UTF_8);JOptionPane.showMessageDialog(frame,"CSV exported");}catch(Exception ex){error(ex.getMessage());}}

    private void notificationPage() {
        currentPage="notifications"; var m=notifications.metrics(LocalDateTime.now()); JPanel page=panel();
        JPanel cards=new JPanel(new GridLayout(1,4,10,8));cards.setOpaque(false);
        cards.add(kpi(tr("در صف ارسال","Queued"),String.valueOf(m.queued())));cards.add(kpi(tr("ثبت‌شده در پیش‌نمایش","Previewed"),String.valueOf(m.sent())));
        cards.add(kpi(tr("ناموفق","Failed"),String.valueOf(m.failed())));cards.add(kpi(tr("آماده پردازش","Due now"),String.valueOf(m.dueNow())));page.add(cards,BorderLayout.NORTH);
        DefaultTableModel model=new DefaultTableModel(new Object[]{"ID",tr("کانال","Channel"),tr("گیرنده","Recipient"),tr("موضوع","Subject"),tr("وضعیت","Status"),tr("تلاش‌ها","Attempts"),tr("ارسال بعدی","Next attempt"),tr("خطا","Last error")},0){public boolean isCellEditable(int r,int c){return false;}};
        for(var x:notifications.messages())model.addRow(new Object[]{x.id(),x.channel(),x.recipient(),x.subject(),x.status(),x.attempts(),x.nextAttemptAt(),x.lastError()});
        JTable table=new JTable(model);table.setRowHeight(28);page.add(new JScrollPane(table),BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEADING));actions.setOpaque(false);
        actions.add(button("ایجاد اعلان / Queue notification","Queue notification",this::notificationDialog));
        actions.add(button("پردازش صف / Process queue","Process queue",()->{int n=notifications.processDue(LocalDateTime.now());JOptionPane.showMessageDialog(frame,tr("تعداد پردازش‌شده: ","Processed: ")+n+tr(" (حالت پیش‌نمایش؛ ارسال واقعی انجام نمی‌شود)"," (preview mode; no real delivery)"));notificationPage();}));
        actions.add(button("خروجی CSV / Export CSV","Export CSV",this::notificationExport));page.add(actions,BorderLayout.SOUTH);
        JLabel note=new JLabel(tr("حالت توسعه: پیام‌ها فقط در فایل پیش‌نمایش ثبت می‌شوند؛ ارسال واقعی ایمیل یا پیامک فعال نیست.","Development mode: messages are written to a preview log; real email/SMS delivery is not enabled."));
        page.add(note,BorderLayout.WEST);show("مدیریت اعلان‌ها","Notification Center",page);
    }
    private void notificationDialog(){
        JComboBox<NotificationService.Channel> channel=new JComboBox<>(NotificationService.Channel.values());JTextField recipient=new JTextField(22),subject=new JTextField(22);JTextArea body=new JTextArea(5,22);body.setLineWrap(true);body.setWrapStyleWord(true);
        JPanel f=new JPanel(new GridLayout(0,2,6,6));for(Object[] r:new Object[][]{{"Channel",channel},{"Recipient",recipient},{"Subject",subject},{"Body",new JScrollPane(body)}}){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}
        if(JOptionPane.showConfirmDialog(frame,f,tr("ثبت اعلان جدید در صف","Queue a new notification"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{notifications.enqueue((NotificationService.Channel)channel.getSelectedItem(),recipient.getText(),subject.getText(),body.getText(),LocalDateTime.now());notificationPage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void notificationExport(){JFileChooser c=new JFileChooser();c.setSelectedFile(new File("notifications.csv"));if(c.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION)try{Files.writeString(c.getSelectedFile().toPath(),"\uFEFF"+notifications.csv(),java.nio.charset.StandardCharsets.UTF_8);JOptionPane.showMessageDialog(frame,"CSV exported");}catch(Exception ex){error(ex.getMessage());}}

    private void housekeepingQualityPage() {
        currentPage="housekeeping-quality";
        JPanel page=panel();
        JPanel cards=new JPanel(new GridLayout(1,3,10,8));cards.setOpaque(false);
        cards.add(kpi(tr("بازرسی‌ها","Inspections"),String.valueOf(housekeepingQuality.all().size())));
        cards.add(kpi(tr("تأییدشده","Approved"),String.valueOf(housekeepingQuality.approved())));
        cards.add(kpi(tr("نیازمند نظافت مجدد","Re-clean required"),String.valueOf(housekeepingQuality.rework())));
        page.add(cards,BorderLayout.NORTH);
        DefaultTableModel model=new DefaultTableModel(new Object[]{"ID",tr("تاریخ","Date"),tr("اتاق","Room"),tr("بازرس","Inspector"),tr("امتیاز","Score"),tr("نتیجه","Result"),tr("توضیحات","Notes")},0){public boolean isCellEditable(int r,int c){return false;}};
        for(var x:housekeepingQuality.all())model.addRow(new Object[]{x.id(),x.inspectedAt(),x.roomId(),x.inspector(),x.score()+"/100",x.status(),x.notes()});
        JTable table=new JTable(model);table.setRowHeight(29);table.setAutoCreateRowSorter(true);page.add(new JScrollPane(table),BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEADING));actions.setOpaque(false);
        actions.add(button("ثبت بازرسی / New inspection","New inspection",this::housekeepingQualityDialog));
        actions.add(button("خروجی CSV / Export","Export CSV",this::housekeepingQualityExport));
        page.add(actions,BorderLayout.SOUTH);show("کنترل کیفیت نظافت","Housekeeping Quality Control & Inspection",page);
    }
    private void housekeepingQualityDialog(){
        if(repository.rooms.isEmpty()){error("No rooms available");return;}
        JComboBox<String> room=maintenanceRoomPicker();JTextField inspector=new JTextField(currentUser,14),notes=new JTextField(20),before=new JTextField(20),after=new JTextField(20);
        JSpinner clean=new JSpinner(new SpinnerNumberModel(5,1,5,1)),bath=new JSpinner(new SpinnerNumberModel(5,1,5,1)),amen=new JSpinner(new SpinnerNumberModel(5,1,5,1)),linen=new JSpinner(new SpinnerNumberModel(5,1,5,1)),safety=new JSpinner(new SpinnerNumberModel(5,1,5,1));
        JPanel f=new JPanel(new GridLayout(0,2,6,6));Object[][] rows={{"Room",room},{"Inspector",inspector},{"Cleanliness (1-5)",clean},{"Bathroom (1-5)",bath},{"Amenities (1-5)",amen},{"Linen (1-5)",linen},{"Safety (1-5)",safety},{"Notes",notes},{"Before photo path (optional)",before},{"After photo path (optional)",after}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}
        if(JOptionPane.showConfirmDialog(frame,f,tr("بازرسی کیفیت اتاق","Room quality inspection"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{var x=housekeepingQuality.inspect((String)room.getSelectedItem(),inspector.getText(),(int)clean.getValue(),(int)bath.getValue(),(int)amen.getValue(),(int)linen.getValue(),(int)safety.getValue(),notes.getText(),before.getText(),after.getText(),LocalDateTime.now());housekeeping.update(x.roomId(),x.status()==HousekeepingQualityService.Status.APPROVED?HousekeepingService.Status.CLEAN:HousekeepingService.Status.DIRTY);JOptionPane.showMessageDialog(frame,x.status()==HousekeepingQualityService.Status.APPROVED?tr("اتاق تأیید شد","Room approved"):tr("نظافت مجدد لازم است","Re-cleaning required"));housekeepingQualityPage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void housekeepingQualityExport(){JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File("housekeeping-quality.csv"));if(chooser.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION)try{Files.writeString(chooser.getSelectedFile().toPath(),"\uFEFF"+housekeepingQuality.csv(),java.nio.charset.StandardCharsets.UTF_8);JOptionPane.showMessageDialog(frame,"CSV exported");}catch(Exception ex){error(ex.getMessage());}}

    private void maintenancePage() {
        currentPage = "maintenance";
        JPanel page=panel(), top=new JPanel(new FlowLayout(FlowLayout.LEADING));
        top.add(button("ثبت خرابی / Report issue","Report issue",this::maintenanceReportDialog));
        top.add(button("برنامه سرویس دوره‌ای / Add PM plan","Add preventive plan",this::maintenancePlanDialog));
        top.add(button("شروع تعمیر / Start work","Start work",()->maintenanceUpdate(MaintenanceIntelligenceService.Status.IN_PROGRESS)));
        top.add(button("تکمیل تعمیر / Complete","Complete",()->maintenanceUpdate(MaintenanceIntelligenceService.Status.COMPLETED)));
        top.add(button("خروجی CSV / Export","Export CSV",this::maintenanceExport));
        page.add(top,BorderLayout.NORTH);
        JPanel body=new JPanel(new BorderLayout(8,8));
        List<MaintenanceIntelligenceService.DueItem> due=new ArrayList<>(maintenance.dueItems(LocalDate.now()));
        for(Room room:repository.rooms){
            boolean arrival=repository.bookings.stream().anyMatch(b->b.roomId.equals(room.id)&&b.start.isAfter(LocalDate.now().minusDays(1))&&!b.start.isAfter(LocalDate.now().plusDays(2))&&!b.status.equalsIgnoreCase("CANCELLED"));
            HousekeepingService.Status hs=housekeeping.status(room.id);
            if(arrival&&(hs==HousekeepingService.Status.DIRTY||hs==HousekeepingService.Status.IN_PROGRESS||hs==HousekeepingService.Status.OUT_OF_SERVICE))
                due.add(new MaintenanceIntelligenceService.DueItem(room.id,"Room readiness",LocalDate.now(),"Upcoming arrival; cleaning status is "+hs,MaintenanceIntelligenceService.Priority.HIGH));
            if(hs==HousekeepingService.Status.OUT_OF_SERVICE)
                due.add(new MaintenanceIntelligenceService.DueItem(room.id,"Room equipment",LocalDate.now(),"Room is marked out of service",MaintenanceIntelligenceService.Priority.URGENT));
        }
        JPanel kpis=new JPanel(new GridLayout(1,4,8,8));
        long open=maintenance.workOrders().stream().filter(w->w.status()!=MaintenanceIntelligenceService.Status.COMPLETED).count();
        long overdue=due.stream().filter(d->d.dueOn().isBefore(LocalDate.now())).count();
        long urgent=due.stream().filter(d->d.priority()==MaintenanceIntelligenceService.Priority.URGENT).count();
        kpis.add(kpi(tr("دستورکار باز","Open work orders"),String.valueOf(open)));
        kpis.add(kpi(tr("سرویس‌های برنامه‌ریزی‌شده","Preventive plans"),String.valueOf(maintenance.plans().size())));
        kpis.add(kpi(tr("موارد سررسید گذشته","Overdue items"),String.valueOf(overdue)));
        kpis.add(kpi(tr("موارد بحرانی","Urgent alerts"),String.valueOf(urgent)));
        body.add(kpis,BorderLayout.NORTH);
        DefaultTableModel dueModel=new DefaultTableModel(new Object[]{"Room","Asset","Due","Reason","Priority"},0){public boolean isCellEditable(int r,int c){return false;}};
        for(var d:due)dueModel.addRow(new Object[]{d.roomId(),d.asset(),d.dueOn(),d.reason(),d.priority()});
        JTable dueTable=new JTable(dueModel);dueTable.setRowHeight(25);
        DefaultTableModel orderModel=new DefaultTableModel(new Object[]{"ID","Room","Asset","Description","Priority","Status","Due","Assignee"},0){public boolean isCellEditable(int r,int c){return false;}};
        for(var w:maintenance.workOrders())orderModel.addRow(new Object[]{w.id(),w.roomId(),w.asset(),w.description(),w.priority(),w.status(),w.dueOn(),w.assignee()});
        JTable orderTable=new JTable(orderModel);orderTable.setRowHeight(25);orderTable.setAutoCreateRowSorter(true);
        JPanel upper=new JPanel(new BorderLayout());upper.add(new JLabel(tr("هشدارهای پیش‌بینانه و سرویس‌های سررسیدشده","Predictive alerts & due preventive maintenance")),BorderLayout.NORTH);upper.add(new JScrollPane(dueTable));
        JPanel lower=new JPanel(new BorderLayout());lower.add(new JLabel(tr("دستورکارهای تعمیر و نگهداری","Maintenance work orders")),BorderLayout.NORTH);lower.add(new JScrollPane(orderTable));
        JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,upper,lower);split.setResizeWeight(.42);body.add(split,BorderLayout.CENTER);
        page.add(body,BorderLayout.CENTER);show("نگهداری هوشمند و تعمیرات پیشگیرانه","Maintenance Intelligence & Predictive Maintenance",page);
    }
    private JComboBox<String> maintenanceRoomPicker(){return new JComboBox<>(repository.rooms.stream().map(Room::id).toArray(String[]::new));}
    private void maintenanceReportDialog(){
        if(repository.rooms.isEmpty()){error("No rooms configured");return;}
        JComboBox<String> room=maintenanceRoomPicker();JTextField asset=new JTextField("HVAC",12),description=new JTextField(20),reported=new JTextField(LocalDate.now().toString(),10),due=new JTextField(LocalDate.now().plusDays(2).toString(),10),assignee=new JTextField(12),notes=new JTextField(16);JComboBox<MaintenanceIntelligenceService.Priority> priority=new JComboBox<>(MaintenanceIntelligenceService.Priority.values());
        JPanel f=new JPanel(new GridLayout(0,2,5,5));Object[][] rows={{"Room",room},{"Asset",asset},{"Issue",description},{"Priority",priority},{"Reported (yyyy-MM-dd)",reported},{"Due (yyyy-MM-dd)",due},{"Assignee",assignee},{"Notes",notes}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}
        if(JOptionPane.showConfirmDialog(frame,f,tr("ثبت خرابی جدید","Report maintenance issue"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{maintenance.report((String)room.getSelectedItem(),asset.getText(),description.getText(),(MaintenanceIntelligenceService.Priority)priority.getSelectedItem(),LocalDate.parse(reported.getText()),LocalDate.parse(due.getText()),assignee.getText(),notes.getText());maintenancePage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void maintenancePlanDialog(){
        if(repository.rooms.isEmpty()){error("No rooms configured");return;}
        JComboBox<String> room=maintenanceRoomPicker();JTextField asset=new JTextField("Elevator",12),interval=new JTextField("90",6),last=new JTextField(LocalDate.now().toString(),10),assignee=new JTextField(12);
        JPanel f=new JPanel(new GridLayout(0,2,5,5));Object[][] rows={{"Room",room},{"Asset",asset},{"Interval (days)",interval},{"Last serviced (yyyy-MM-dd)",last},{"Assignee",assignee}};for(Object[] r:rows){f.add(new JLabel((String)r[0]));f.add((Component)r[1]);}
        if(JOptionPane.showConfirmDialog(frame,f,tr("برنامه سرویس دوره‌ای","Preventive maintenance plan"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{maintenance.savePlan((String)room.getSelectedItem(),asset.getText(),Integer.parseInt(interval.getText()),LocalDate.parse(last.getText()),assignee.getText());maintenancePage();}catch(Exception ex){error(ex.getMessage());}
    }
    private void maintenanceUpdate(MaintenanceIntelligenceService.Status status){List<MaintenanceIntelligenceService.WorkOrder> open=maintenance.workOrders().stream().filter(w->w.status()!=MaintenanceIntelligenceService.Status.COMPLETED).toList();if(open.isEmpty()){error("No open work orders");return;}JComboBox<MaintenanceIntelligenceService.WorkOrder> pick=new JComboBox<>(open.toArray(MaintenanceIntelligenceService.WorkOrder[]::new));if(JOptionPane.showConfirmDialog(frame,pick,tr("انتخاب دستورکار","Select work order"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{maintenance.changeStatus(((MaintenanceIntelligenceService.WorkOrder)pick.getSelectedItem()).id(),status);maintenancePage();}catch(Exception ex){error(ex.getMessage());}}
    private void maintenanceExport(){JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File("hotel-maintenance-work-orders.csv"));if(chooser.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION)try{Files.writeString(chooser.getSelectedFile().toPath(),"\uFEFF"+maintenance.csv(),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception ex){error(ex.getMessage());}}

    private void rooms() {
        currentPage = "rooms";
        JPanel p=panel(); List<String[]> rows=new ArrayList<>();
        for(Room r:repository.rooms) rows.add(new String[]{r.id,r.type,String.format("%.2f",r.rate),repository.active(r.id)?tr("اشغال","Occupied"):tr("آزاد","Available")});
        p.add(new JScrollPane(table(new String[]{tr("اتاق","Room"),tr("نوع","Type"),tr("نرخ","Rate"),tr("وضعیت","Status")},rows)),BorderLayout.CENTER);
        p.add(button("افزودن اتاق","Add room",()->{
            if (!requireAdmin()) return;
            JTextField id=new JTextField(),type=new JTextField("Standard"),rate=new JTextField("120");
            if(form("New room",new String[]{"Room number","Type","Nightly rate"},new JTextField[]{id,type,rate})) try {
                String key=id.getText().trim(); if(key.isBlank()||repository.rooms.stream().anyMatch(r->r.id.equals(key))) throw new IllegalArgumentException("Room number must be unique");
                double price=Double.parseDouble(rate.getText().trim()); if(price<=0)throw new IllegalArgumentException("Rate must be positive");
                repository.rooms.add(new Room(key,type.getText().trim(),price));repository.save();rooms();
            }catch(Exception ex){error(ex.getMessage());}
        }),BorderLayout.SOUTH); show("مدیریت اتاق‌ها","Rooms",p);
    }
    private void guests() {
        currentPage = "guests";
        JPanel p=panel(); List<String[]> rows=new ArrayList<>(); for(Guest g:repository.guests)rows.add(new String[]{g.id,g.name,g.phone});
        p.add(new JScrollPane(table(new String[]{tr("شناسه","ID"),tr("نام","Name"),tr("تلفن","Phone")},rows)),BorderLayout.CENTER);
        p.add(button("افزودن مهمان","Add guest",()->{
            JTextField name=new JTextField(),phone=new JTextField();
            if(form("New guest",new String[]{"Full name","Phone"},new JTextField[]{name,phone})){
                if(name.getText().isBlank()){error(tr("نام مهمان الزامی است","Name required"));return;}
                repository.guests.add(new Guest(UUID.randomUUID().toString().substring(0,8),name.getText().trim(),phone.getText().trim()));repository.save();guests();
            }
        }),BorderLayout.SOUTH);show("مدیریت مهمان‌ها","Guests",p);
    }
    private void bookings() {
        currentPage = "bookings";
        JPanel p=panel();List<String[]> rows=new ArrayList<>();
        for(Booking b:repository.bookings) rows.add(new String[]{b.id,b.roomId,b.guestId,b.start.toString(),b.end.toString(),b.status});
        JTable t=table(new String[]{tr("شناسه","ID"),tr("اتاق","Room"),tr("شناسه مهمان","Guest ID"),tr("ورود","Check-in"),tr("خروج","Check-out"),tr("وضعیت","Status")},rows);
        p.add(new JScrollPane(t),BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT)); actions.setOpaque(false);
        actions.add(button("رزرو جدید","New booking",this::newBooking));
        actions.add(button("تقویم رزروها","Reservation calendar",this::reservationCalendarDialog));
        actions.add(button("ورود مهمان","Check in",()->changeBookingStatus(t,"CHECKED_IN")));
        actions.add(button("خروج مهمان","Check out",()->changeBookingStatus(t,"CHECKED_OUT")));
        actions.add(button("صورتحساب","Invoice",()->exportInvoice(t)));
        actions.add(button("لغو رزرو","Cancel selected",()->{
            int index=t.getSelectedRow();if(index<0)return;String id=(String)t.getValueAt(t.convertRowIndexToModel(index),0);
            repository.bookings.stream().filter(b->b.id.equals(id) && b.status.equals("CONFIRMED")).findFirst().ifPresent(b->{b.status="CANCELLED";repository.save();bookings();});
        })); JTextField filter = new JTextField(); filter.setToolTipText(tr("جستجو بر اساس شناسه، اتاق، مهمان یا وضعیت", "Filter by ID, room, guest or status"));
        filter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                String term = filter.getText().toLowerCase(java.util.Locale.ROOT);
                javax.swing.table.TableRowSorter<javax.swing.table.TableModel> sorter = new javax.swing.table.TableRowSorter<>(t.getModel());
                t.setRowSorter(sorter);
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(term)));
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) {update();}
            public void removeUpdate(javax.swing.event.DocumentEvent e) {update();}
            public void changedUpdate(javax.swing.event.DocumentEvent e) {update();}
        });
        JPanel bottom = new JPanel(new BorderLayout(8,8)); bottom.setOpaque(false);
        bottom.add(filter, BorderLayout.NORTH); bottom.add(actions,BorderLayout.SOUTH);
        p.add(bottom,BorderLayout.SOUTH);show("مدیریت رزروها","Bookings",p);
    }

    private void reservationCalendarDialog() {
        java.time.YearMonth[] month = {java.time.YearMonth.now()};
        JDialog dialog = new JDialog(frame, tr("تقویم رزروها", "Reservation calendar"), true);
        dialog.setLayout(new BorderLayout(8,8));
        JPanel header = new JPanel(new BorderLayout());
        JButton previous = new JButton("‹"); JButton next = new JButton("›"); JLabel caption = new JLabel("", SwingConstants.CENTER);
        header.add(previous,BorderLayout.WEST); header.add(caption,BorderLayout.CENTER); header.add(next,BorderLayout.EAST);
        JPanel days = new JPanel(new GridLayout(0,7,4,4));
        dialog.add(header,BorderLayout.NORTH); dialog.add(days,BorderLayout.CENTER);
        Runnable render = () -> {
            caption.setText(month[0].toString()); days.removeAll();
            String[] weekdays = persian ? new String[]{"شنبه","یکشنبه","دوشنبه","سه‌شنبه","چهارشنبه","پنجشنبه","جمعه"} : new String[]{"Mon","Tue","Wed","Thu","Fri","Sat","Sun"};
            for(String day:weekdays){JLabel label=new JLabel(day,SwingConstants.CENTER);label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));days.add(label);}
            java.time.LocalDate first=month[0].atDay(1); int offset=persian?Math.floorMod(first.getDayOfWeek().getValue()+1,7):first.getDayOfWeek().getValue()-1;
            for(int i=0;i<offset;i++)days.add(new JLabel(""));
            for(int d=1;d<=month[0].lengthOfMonth();d++){
                java.time.LocalDate date=month[0].atDay(d); long arrivals=repository.bookings.stream().filter(b->b.start.equals(date)&&!b.status.equals("CANCELLED")).count(); long departures=repository.bookings.stream().filter(b->b.end.equals(date)&&!b.status.equals("CANCELLED")).count(); long staying=repository.bookings.stream().filter(b->!b.status.equals("CANCELLED")&&!date.isBefore(b.start)&&date.isBefore(b.end)).count();
                JPanel tile=new JPanel(new BorderLayout(2,2));tile.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(210,215,225)),BorderFactory.createEmptyBorder(5,5,5,5)));
                JLabel number=new JLabel(Integer.toString(d));number.setFont(number.getFont().deriveFont(java.awt.Font.BOLD,15f));tile.add(number,BorderLayout.NORTH);
                JLabel detail=new JLabel("<html>"+tr("اقامت: ","Stays: ")+staying+"<br>"+tr("ورود: ","In: ")+arrivals+"<br>"+tr("خروج: ","Out: ")+departures+"</html>");detail.setFont(detail.getFont().deriveFont(10f));tile.add(detail,BorderLayout.CENTER);
                if(date.equals(java.time.LocalDate.now()))tile.setBackground(new Color(225,240,255));days.add(tile);
            }
            days.revalidate();days.repaint();
        };
        previous.addActionListener(e->{month[0]=month[0].minusMonths(1);render.run();});next.addActionListener(e->{month[0]=month[0].plusMonths(1);render.run();});
        render.run(); dialog.setSize(760,560);dialog.setLocationRelativeTo(frame);dialog.setVisible(true);
    }

    private void exportInvoice(JTable table) {
        int selected = table.getSelectedRow();
        if (selected < 0) { error(tr("ابتدا یک رزرو را انتخاب کنید", "Select a booking first")); return; }
        String id = String.valueOf(table.getValueAt(table.convertRowIndexToModel(selected), 0));
        Booking booking = repository.bookings.stream().filter(b -> b.id.equals(id)).findFirst().orElse(null);
        if (booking == null) { error("Booking not found"); return; }
        Room room = repository.rooms.stream().filter(r -> r.id.equals(booking.roomId)).findFirst().orElse(null);
        Guest guest = repository.guests.stream().filter(g -> g.id.equals(booking.guestId)).findFirst().orElse(null);
        if (room == null || guest == null) { error("Room or guest record missing"); return; }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("invoice-" + booking.id + ".txt"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try {
            Files.writeString(chooser.getSelectedFile().toPath(), InvoiceService.render(booking, room, guest),
                java.nio.charset.StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(frame, tr("صورتحساب ذخیره شد", "Invoice saved"));
        } catch (IOException ex) { error(ex.getMessage()); }
    }
    private void changeBookingStatus(JTable table, String next) {
        int row = table.getSelectedRow();
        if (row < 0) { error(tr("ابتدا یک رزرو را انتخاب کنید", "Select a booking first")); return; }
        String id = (String)table.getValueAt(table.convertRowIndexToModel(row), 0);
        Booking booking = repository.bookings.stream().filter(b -> b.id.equals(id)).findFirst().orElse(null);
        if (booking == null) return;
        if (next.equals("CHECKED_IN") && !booking.status.equals("CONFIRMED")) {
            error(tr("فقط رزرو تاییدشده قابل پذیرش است", "Only confirmed bookings can check in")); return;
        }
        if (next.equals("CHECKED_OUT") && !booking.status.equals("CHECKED_IN")) {
            error(tr("ابتدا باید مهمان پذیرش شده باشد", "Guest must check in first")); return;
        }
        if (next.equals("CHECKED_IN") && LocalDate.now().isBefore(booking.start)) {
            error(tr("هنوز تاریخ ورود نرسیده است", "Check-in cannot occur before the arrival date")); return;
        }
        booking.status = next;
        repository.save(); bookings();
    }
    private void backup() {
        if (!requireAdmin()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("ashkan-hotelsphere-backup.db"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try {
            repository.save();
            Path destination = chooser.getSelectedFile().toPath();
            if (destination.toAbsolutePath().equals(repository.store.databasePath().toAbsolutePath())) throw new IOException("Choose a different backup path");
            repository.store.backup(destination);
            JOptionPane.showMessageDialog(frame, tr("پشتیبان ذخیره شد", "Backup created") + ": " + destination);
        } catch (Exception e) { error(e.getMessage()); }
    }
    private void newBooking(){
        if(repository.guests.isEmpty()||repository.rooms.isEmpty()){error("Create a guest and room first");return;}
        JTextField room=new JTextField(repository.rooms.get(0).id),guest=new JTextField(repository.guests.get(0).id);
        JTextField start=new JTextField(LocalDate.now().toString()),end=new JTextField(LocalDate.now().plusDays(1).toString());
        if(form("New booking",new String[]{"Room number","Guest ID","Check-in YYYY-MM-DD","Check-out YYYY-MM-DD"},new JTextField[]{room,guest,start,end}))try{
            String roomId=room.getText().trim(),guestId=guest.getText().trim();LocalDate a=LocalDate.parse(start.getText().trim()),z=LocalDate.parse(end.getText().trim());
            if(!z.isAfter(a))throw new IllegalArgumentException("Check-out must follow check-in");
            if (a.isBefore(LocalDate.now())) throw new IllegalArgumentException("Check-in date cannot be in the past");
            if(repository.rooms.stream().noneMatch(r->r.id.equals(roomId)))throw new IllegalArgumentException("Unknown room");
            if(housekeeping.status(roomId)==HousekeepingService.Status.OUT_OF_SERVICE)throw new IllegalArgumentException("Room is out of service");
            if(repository.guests.stream().noneMatch(g->g.id.equals(guestId)))throw new IllegalArgumentException("Unknown guest");
            if(repository.bookings.stream().anyMatch(b->b.roomId.equals(roomId)&&(b.status.equals("CONFIRMED") || b.status.equals("CHECKED_IN"))&&a.isBefore(b.end)&&z.isAfter(b.start)))throw new IllegalArgumentException("Room unavailable for these dates");
            repository.bookings.add(new Booking(UUID.randomUUID().toString().substring(0,8),roomId,guestId,a,z,"CONFIRMED"));repository.save();bookings();
        }catch(Exception ex){error(ex.getMessage());}
    }
    private void payments() {
        currentPage = "payments";
        JPanel p = panel();
        List<String[]> rows = new ArrayList<>();
        for (Payment payment : repository.payments) {
            rows.add(new String[]{payment.id, payment.bookingId, payment.amount.toPlainString(), payment.method, payment.createdAt.toString()});
        }
        JTable t = table(new String[]{"ID", "Booking", "Amount", "Method", "Date"}, rows);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        actions.setOpaque(false);
        actions.add(button("ثبت پرداخت", "Record payment", this::recordPayment));
        p.add(actions, BorderLayout.SOUTH);
        show("مدیریت پرداخت‌ها", "Payments", p);
    }
    private void recordPayment() {
        if (repository.bookings.isEmpty()) { error(tr("ابتدا رزرو ثبت کنید", "Create a booking first")); return; }
        JComboBox<String> bookings = new JComboBox<>(repository.bookings.stream().filter(b -> !b.status.equals("CANCELLED")).map(b -> b.id).toArray(String[]::new));
        if (bookings.getItemCount() == 0) { error("No eligible bookings"); return; }
        JTextField amount = new JTextField();
        JComboBox<String> method = new JComboBox<>(new String[]{"CASH", "CARD", "BANK_TRANSFER"});
        JPanel form = new JPanel(new GridLayout(3, 2, 10, 10));
        form.add(new JLabel(tr("رزرو", "Booking"))); form.add(bookings);
        form.add(new JLabel(tr("مبلغ", "Amount"))); form.add(amount);
        form.add(new JLabel(tr("روش پرداخت", "Method"))); form.add(method);
        if (JOptionPane.showConfirmDialog(frame, form, tr("پرداخت جدید", "New payment"), JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            java.math.BigDecimal value = new java.math.BigDecimal(amount.getText().trim()).setScale(2, java.math.RoundingMode.UNNECESSARY);
            if (value.signum() <= 0) throw new IllegalArgumentException("Payment amount must be positive");
            String bookingId = (String) bookings.getSelectedItem();
            Booking booking = repository.bookings.stream().filter(b -> b.id.equals(bookingId)).findFirst().orElseThrow();
            Room room = repository.rooms.stream().filter(r -> r.id.equals(booking.roomId)).findFirst().orElseThrow();
            java.math.BigDecimal due = java.math.BigDecimal.valueOf(room.rate).multiply(java.math.BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(booking.start, booking.end)));
            java.math.BigDecimal paid = repository.payments.stream().filter(x -> x.bookingId.equals(bookingId)).map(x -> x.amount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            if (paid.add(value).compareTo(due) > 0) throw new IllegalArgumentException("Payment exceeds outstanding balance: " + due.subtract(paid));
            repository.payments.add(new Payment(UUID.randomUUID().toString().substring(0, 8), bookingId, value, (String) method.getSelectedItem(), LocalDateTime.now()));
            repository.save(); payments();
        } catch (Exception ex) { error(ex.getMessage()); }
    }
    private void receivables() {
        currentPage = "receivables";
        if (!requireAdmin()) return;
        JPanel p = panel();
        List<ReceivablesService.Entry> entries = ReceivablesService.calculate(repository.bookings, repository.rooms, repository.payments, LocalDate.now());
        List<String[]> rows = new ArrayList<>();
        java.math.BigDecimal outstanding = java.math.BigDecimal.ZERO;
        for (ReceivablesService.Entry e : entries) {
            outstanding = outstanding.add(e.balance());
            rows.add(new String[]{e.bookingId(), e.roomId(), e.guestId(), e.dueDate().toString(), e.balance().toPlainString(), e.bucket()});
        }
        JLabel total = new JLabel(tr("مطالبات باز: ", "Open receivables: ") + outstanding.toPlainString() + "  |  " + tr("تعداد: ", "Count: ") + entries.size());
        total.setFont(new Font("SansSerif", Font.BOLD, 18));
        p.add(total, BorderLayout.NORTH);
        p.add(new JScrollPane(table(new String[]{"Booking", "Room", "Guest", "Checkout", "Outstanding", "Age"}, rows)), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        actions.add(button("خروجی CSV", "Export CSV", () -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("hotelsphere-receivables.csv"));
            if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
            try (java.io.BufferedWriter w = Files.newBufferedWriter(chooser.getSelectedFile().toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                w.write("Booking,Room,Guest,Checkout,Outstanding,Age\n");
                for (String[] row : rows) { w.write(String.join(",", Arrays.stream(row).map(HotelApp::csv).toArray(String[]::new))); w.newLine(); }
                JOptionPane.showMessageDialog(frame, tr("فایل ذخیره شد", "Report saved"));
            } catch (IOException ex) { error(ex.getMessage()); }
        }));
        p.add(actions, BorderLayout.SOUTH);
        show("مطالبات و سررسید بدهی", "Receivables aging", p);
    }
    private void finance() {
        currentPage = "finance";
        if (!requireAdmin()) return;
        JPanel p = panel();
        java.util.List<FinanceService.Balance> balances = FinanceService.balances(repository.bookings, repository.rooms, repository.payments);
        java.math.BigDecimal total = java.math.BigDecimal.ZERO, paid = java.math.BigDecimal.ZERO, outstanding = java.math.BigDecimal.ZERO;
        List<String[]> rows = new ArrayList<>();
        for (FinanceService.Balance b : balances) {
            total = total.add(b.total()); paid = paid.add(b.paid()); outstanding = outstanding.add(b.outstanding());
            rows.add(new String[]{b.bookingId(), b.status(), b.total().toPlainString(), b.paid().toPlainString(), b.outstanding().toPlainString()});
        }
        JLabel summary = new JLabel(tr("ارزش اقامت: ", "Stay value: ") + total + "    |    " + tr("دریافتی: ", "Collected: ") + paid + "    |    " + tr("مانده: ", "Outstanding: ") + outstanding);
        summary.setFont(new Font("SansSerif", Font.BOLD, 17)); summary.setBorder(new EmptyBorder(12, 6, 18, 6));
        p.add(summary, BorderLayout.NORTH);
        p.add(new JScrollPane(table(new String[]{"Booking", "Status", "Total", "Paid", "Balance"}, rows)), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING)); actions.setOpaque(false);
        actions.add(button("خروجی CSV مالی", "Export finance CSV", () -> exportFinance(balances)));
        p.add(actions, BorderLayout.SOUTH);
        show("گزارش مالی و بدهی", "Finance & outstanding balances", p);
    }
    private void exportFinance(java.util.List<FinanceService.Balance> balances) {
        if (!requireAdmin()) return;
        JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new File("hotelsphere-finance.csv"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try (java.io.BufferedWriter w = Files.newBufferedWriter(chooser.getSelectedFile().toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
            w.write("Booking ID,Status,Total,Paid,Outstanding\n");
            for (FinanceService.Balance b : balances) {
                w.write(String.join(",", csv(b.bookingId()), csv(b.status()), csv(b.total().toPlainString()), csv(b.paid().toPlainString()), csv(b.outstanding().toPlainString())));
                w.newLine();
            }
            JOptionPane.showMessageDialog(frame, tr("گزارش مالی ذخیره شد", "Finance report saved"));
        } catch (IOException ex) { error(ex.getMessage()); }
    }
    private void reports(){
        currentPage = "reports";
        if (!requireAdmin()) return;
        JPanel page = panel();
        JTextField from = new JTextField(LocalDate.now().minusDays(6).toString(), 10);
        JTextField through = new JTextField(LocalDate.now().toString(), 10);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 8)); filters.setOpaque(false);
        filters.add(new JLabel(tr("از تاریخ (YYYY-MM-DD)", "From (YYYY-MM-DD)"))); filters.add(from);
        filters.add(new JLabel(tr("تا تاریخ (YYYY-MM-DD)", "Through (YYYY-MM-DD)"))); filters.add(through);
        JComboBox<String> paymentMethod = new JComboBox<>();
        paymentMethod.addItem(tr("همه روش‌ها", "All methods"));
        repository.payments.stream().map(p -> p.method() == null || p.method().isBlank() ? "Unspecified" : p.method().trim())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).forEach(paymentMethod::addItem);
        filters.add(new JLabel(tr("روش پرداخت", "Payment method"))); filters.add(paymentMethod);
        JButton refresh = button("ساخت گزارش", "Generate report", () -> {});
        filters.add(refresh); page.add(filters, BorderLayout.NORTH);
        JPanel results = new JPanel(new BorderLayout(10, 10)); results.setOpaque(false);
        JPanel summaryBox = new JPanel(new GridLayout(2, 1, 4, 4)); summaryBox.setOpaque(false);
        JLabel summaryLabel = new JLabel(); summaryLabel.setBorder(new EmptyBorder(10, 4, 4, 4));
        summaryLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        JLabel methodLabel = new JLabel(); methodLabel.setBorder(new EmptyBorder(0, 4, 8, 4));
        summaryBox.add(summaryLabel); summaryBox.add(methodLabel);
        DefaultTableModel model = new DefaultTableModel(new String[]{"Date", tr("اتاق اشغال", "Occupied rooms"), tr("اشغال ٪", "Occupancy %"), tr("دریافتی روز", "Daily payments")}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable grid = new JTable(model); grid.setAutoCreateRowSorter(true); grid.setRowHeight(30);
        ReportChartPanel chart = new ReportChartPanel(persian);
        JSplitPane reportSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chart, new JScrollPane(grid));
        reportSplit.setResizeWeight(0.38); reportSplit.setBorder(null); reportSplit.setContinuousLayout(true);
        results.add(summaryBox, BorderLayout.NORTH); results.add(reportSplit, BorderLayout.CENTER);
        JButton export = button("خروجی CSV", "Export CSV", () -> {});
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING)); actions.setOpaque(false); actions.add(export);
        results.add(actions, BorderLayout.SOUTH); page.add(results, BorderLayout.CENTER);
        final PeriodReportService.Summary[] current = new PeriodReportService.Summary[1];
        Runnable build = () -> {
            try {
                String selectedMethod = paymentMethod.getSelectedIndex() <= 0 ? "" : String.valueOf(paymentMethod.getSelectedItem());
                var report = PeriodReportService.summarize(repository.rooms, repository.bookings, repository.payments,
                        LocalDate.parse(from.getText().trim()), LocalDate.parse(through.getText().trim()), selectedMethod);
                current[0] = report; model.setRowCount(0); chart.setDaily(report.daily());
                for (var day : report.daily()) model.addRow(new Object[]{day.date(), day.occupiedRooms(), day.occupancyPercent() + "%", day.paymentsReceived().toPlainString()});
                summaryLabel.setText(tr("رزروهای هم‌پوشان: ", "Overlapping bookings: ") + report.bookingCount()
                        + "   |   " + tr("اتاق‌شب: ", "Room nights: ") + report.roomNights()
                        + "   |   " + tr("دریافتی بازه: ", "Payments in period: ") + report.paymentsReceived().toPlainString()
                        + "   |   " + tr("مانده کل: ", "Total outstanding: ") + report.outstanding().toPlainString());
                StringBuilder methods = new StringBuilder(tr("روش‌های پرداخت: ", "Payment methods: "));
                if (report.methodTotals().isEmpty()) methods.append(tr("بدون پرداخت در این بازه", "No payments in this period"));
                else for (var item : report.methodTotals()) {
                    if (methods.length() > tr("روش‌های پرداخت: ", "Payment methods: ").length()) methods.append("   |   ");
                    methods.append(item.method()).append(": ").append(item.amount().toPlainString()).append(" (").append(item.transactionCount()).append(")");
                }
                methodLabel.setText(methods.toString());
            } catch (Exception ex) { current[0] = null; error(tr("تاریخ‌ها را بررسی کنید: ", "Check the date range: ") + ex.getMessage()); }
        };
        refresh.addActionListener(e -> build.run());
        paymentMethod.addActionListener(e -> { if (current[0] != null) build.run(); });
        export.addActionListener(e -> {
            if (current[0] == null) { error(tr("ابتدا گزارش را بسازید", "Generate a report first")); return; }
            JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new File("hotelsphere-period-report.csv"));
            if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
            try { Files.writeString(chooser.getSelectedFile().toPath(), PeriodReportService.toCsv(current[0]), java.nio.charset.StandardCharsets.UTF_8);
                JOptionPane.showMessageDialog(frame, tr("گزارش ذخیره شد", "Report saved"));
            } catch (IOException ex) { error(ex.getMessage()); }
        });
        build.run();
        show("گزارش مدیریتی", "Management report", page);
    }
    private static final class ReportChartPanel extends JPanel {
        private List<PeriodReportService.Daily> daily = List.of();
        private final boolean persian;
        ReportChartPanel(boolean persian) { this.persian = persian; setPreferredSize(new Dimension(500, 190)); setMinimumSize(new Dimension(300, 130)); setBackground(Color.WHITE); }
        void setDaily(List<PeriodReportService.Daily> value) { daily = List.copyOf(value); repaint(); }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int left = 48, right = getWidth() - 22, top = 22, bottom = getHeight() - 30;
                if (daily.isEmpty() || right <= left || bottom <= top) return;
                g.setColor(new Color(225, 231, 239));
                for (int i = 0; i <= 4; i++) { int y = top + (bottom-top)*i/4; g.drawLine(left,y,right,y); }
                java.math.BigDecimal max = daily.stream().map(PeriodReportService.Daily::paymentsReceived).max(java.math.BigDecimal::compareTo).orElse(java.math.BigDecimal.ZERO);
                if (max.signum() == 0) max = java.math.BigDecimal.ONE;
                int step = Math.max(1, (right-left)/daily.size()); int barWidth = Math.max(2, Math.min(18, step-3));
                int previousX = -1, previousY = -1;
                for (int i = 0; i < daily.size(); i++) {
                    var day = daily.get(i); int x = left + i*step + Math.max(0,(step-barWidth)/2);
                    int barHeight = day.paymentsReceived().multiply(java.math.BigDecimal.valueOf(bottom-top)).divide(max, 0, java.math.RoundingMode.HALF_UP).intValue();
                    g.setColor(new Color(0,151,145)); g.fillRoundRect(x, bottom-barHeight, barWidth, Math.max(0,barHeight), 4, 4);
                    int px = left + i*step + step/2; int py = bottom - (bottom-top)*day.occupancyPercent()/100;
                    if (previousX >= 0) { g.setColor(new Color(237, 127, 45)); g.setStroke(new BasicStroke(2f)); g.drawLine(previousX,previousY,px,py); }
                    g.setColor(new Color(237,127,45)); g.fillOval(px-3,py-3,6,6); previousX=px; previousY=py;
                    int labelEvery = Math.max(1, daily.size()/7);
                    if (i % labelEvery == 0 || i == daily.size()-1) { g.setColor(new Color(80,91,108)); String label = day.date().toString().substring(5); g.drawString(label, Math.max(left,px-15), bottom+18); }
                }
                g.setFont(getFont().deriveFont(Font.BOLD, 11f)); g.setColor(new Color(0,151,145)); g.drawString("■ " + (persian ? "دریافتی" : "Payments"), left, 14);
                g.setColor(new Color(237,127,45)); g.drawString("— " + (persian ? "اشغال ٪" : "Occupancy %"), left+95, 14);
            } finally { g.dispose(); }
        }
    }
    private boolean form(String title,String[] labels,JTextField[] fields){JPanel p=new JPanel(new GridLayout(labels.length,2,10,10));for(int i=0;i<labels.length;i++){p.add(new JLabel(labels[i]));p.add(fields[i]);}return JOptionPane.showConfirmDialog(frame,p,title,JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION;}
    private void error(String message){JOptionPane.showMessageDialog(frame,message,"Validation",JOptionPane.ERROR_MESSAGE);}
    record Room(String id,String type,double rate) implements Serializable {}
    record Payment(String id, String bookingId, java.math.BigDecimal amount, String method, LocalDateTime createdAt) implements Serializable {}
    record Guest(String id,String name,String phone) implements Serializable {}
    static final class Booking implements Serializable {final String id,guestId;String roomId;LocalDate start,end;String status;Booking(String id,String roomId,String guestId,LocalDate start,LocalDate end,String status){this.id=id;this.roomId=roomId;this.guestId=guestId;this.start=start;this.end=end;this.status=status;}}
    static final class Repository {
        final List<Room> rooms = new ArrayList<>();
        final List<Guest> guests = new ArrayList<>();
        final List<Booking> bookings = new ArrayList<>();
        final List<Payment> payments = new ArrayList<>();
        final SqliteHotelStore store = new SqliteHotelStore();
        Repository() {
            try {
                store.load(rooms, guests, bookings, payments);
                if (rooms.isEmpty()) {
                    rooms.add(new Room("101", "Standard", 120));
                    rooms.add(new Room("102", "Deluxe", 180));
                    rooms.add(new Room("201", "Suite", 320));
                    save();
                }
            } catch (Exception ex) {
                throw new IllegalStateException("Hotel database initialization failed. Your data was not overwritten: " + ex.getMessage(), ex);
            }
        }
        boolean active(String roomId) {
            LocalDate now = LocalDate.now();
            return bookings.stream().anyMatch(b -> b.roomId.equals(roomId)
                && (b.status.equals("CONFIRMED") || b.status.equals("CHECKED_IN"))
                && !now.isBefore(b.start) && now.isBefore(b.end));
        }
        void save() {
            try { store.save(rooms, guests, bookings, payments); }
            catch (Exception ex) {
                throw new IllegalStateException("SQLite save failed: " + ex.getMessage(), ex);
            }
        }
    }
}
