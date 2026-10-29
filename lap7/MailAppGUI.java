import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;

/**
 * Ứng dụng SMTP & POP3 Client hoàn chỉnh viết bằng Java Swing
 * Giao diện Dark Theme hiện đại theo bản vẽ thiết kế di động.
 */
public class MailAppGUI extends JFrame {

    // --- Bảng màu chuẩn theo Mockup ---
    public static final Color BG_DARKEST = new Color(13, 17, 23);       // #0D1117
    public static final Color BG_CARD = new Color(22, 27, 34);          // #161B22
    public static final Color BG_INPUT = new Color(28, 35, 51);         // #1C2333
    public static final Color BORDER_COLOR = new Color(48, 54, 61);      // #30363D
    public static final Color GREEN_PRIMARY = new Color(0, 255, 136);    // #00FF88
    public static final Color GREEN_SECONDARY = new Color(16, 185, 129); // #10B981
    public static final Color TEXT_PRIMARY = new Color(230, 237, 243);   // #E6EDF3
    public static final Color TEXT_MUTED = new Color(139, 148, 158);     // #8B949E
    public static final Color TEXT_SUBTLE = new Color(72, 79, 88);       // #484F58
    public static final Color DANGER_RED = new Color(248, 81, 73);       // #F85149

    // --- State & Controllers ---
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentCards = new JPanel(cardLayout);
    private String currentTab = "INBOX";

    // Bottom nav buttons
    private JButton btnNavInbox, btnNavCompose, btnNavLogs, btnNavAccount;

    // Tab 1: Inbox UI Components
    private JPanel emailListContainer;
    private JLabel pop3StatusDot;
    private JLabel pop3StatusHost;
    private JLabel pop3StatusStats;
    private JButton btnFetchMail;
    private final List<EmailModel> currentEmails = new ArrayList<>();

    // Tab 2: Compose UI Components
    private JTextField txtComposeFrom;
    private JTextField txtComposeTo;
    private JTextField txtComposeSubject;
    private JTextArea txtComposeBody;
    private JButton btnSendData;
    private JLabel smtpStatusHost;
    private JPanel sentEmailListContainer;
    private boolean showingCompose = true; // true = soạn thư, false = thư đã gửi

    // Tab 3: Logs UI Components
    private JTextPane logTextPane;
    private StyledDocument logDoc;
    private JTextField txtLogInput;
    private JButton btnLogFilterAll, btnLogFilterSmtp, btnLogFilterPop3;
    private String currentLogFilter = "ALL";

    // Tab 4: Account UI Components
    private JTextField txtPop3Server;
    private JTextField txtPop3Port;
    private JTextField txtSmtpServer;
    private JTextField txtSmtpPort;
    private JTextField txtAccountEmail;
    private JPasswordField txtAccountPassword;
    private JButton btnCheckConnection;
    private JLabel lblDbStatus; // label trạng thái DB - cập nhật động

    // Clock label
    private JLabel clockLabel;

    public static void main(String[] args) {
        // Thiết lập giao diện mượt mà
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            MailAppGUI app = new MailAppGUI();
            app.setVisible(true);
        });
    }

    public MailAppGUI() {
        // Bắt buộc nạp và khởi tạo DatabaseManager TRƯỚC khi tạo UI
        boolean dbOk = DatabaseManager.isConnected();
        System.out.println("[GUI] DatabaseManager.isConnected() = " + dbOk);

        setTitle("SMTP / POP3 Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(440, 840);
        setMinimumSize(new Dimension(380, 700));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARKEST);
        setLayout(new BorderLayout());

        // 1. Top Phone Status Bar (Giờ, sóng, wifi, pin)
        add(createPhoneStatusBar(), BorderLayout.NORTH);

        // 2. Middle Content Area (CardLayout chứa 4 tab)
        contentCards.setBackground(BG_DARKEST);
        contentCards.add(createInboxPanel(), "INBOX");
        contentCards.add(createComposePanel(), "COMPOSE");
        contentCards.add(createLogsPanel(), "LOGS");
        contentCards.add(createAccountPanel(), "ACCOUNT");
        add(contentCards, BorderLayout.CENTER);

        // 3. Bottom Navigation Bar (4 Tab)
        add(createBottomNav(), BorderLayout.SOUTH);

        // Đăng ký nhận log thời gian thực
        setupProtocolLogListener();

        // Nạp dữ liệu từ Database MySQL hoặc mẫu ban đầu
        loadDataFromDatabaseOrSample();

        // Chạy đồng hồ
        startClock();

        // Chuyển tới tab mặc định
        switchTab("INBOX");
    }

    // =========================================================================
    // 1. TOP PHONE STATUS BAR
    // =========================================================================
    private JPanel createPhoneStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BG_DARKEST);
        bar.setBorder(new EmptyBorder(8, 20, 6, 20));

        clockLabel = new JLabel("09:41");
        clockLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        clockLabel.setForeground(TEXT_PRIMARY);

        JLabel iconsLabel = new JLabel("📶 📶 🔋 100%");
        iconsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        iconsLabel.setForeground(TEXT_MUTED);

        bar.add(clockLabel, BorderLayout.WEST);
        bar.add(iconsLabel, BorderLayout.EAST);
        return bar;
    }

    private void startClock() {
        javax.swing.Timer timer = new javax.swing.Timer(1000, e -> {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            clockLabel.setText(sdf.format(new Date()));
        });
        timer.start();
    }

    // =========================================================================
    // 2. TAB 1: HỘP THƯ ĐẾN (POP3 CLIENT)
    // =========================================================================
    private JPanel createInboxPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARKEST);

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(BG_DARKEST);
        headerPanel.setBorder(new EmptyBorder(10, 20, 10, 20));

        JLabel title = new JLabel("Hộp thư đến");
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(TEXT_PRIMARY);

        JButton btnMore = createRoundIconButton("⋯");
        btnMore.addActionListener(e -> fetchEmailsAction());

        headerPanel.add(title, BorderLayout.WEST);
        headerPanel.add(btnMore, BorderLayout.EAST);

        // POP3 Connection Status Box
        JPanel statusBox = new JPanel(new BorderLayout());
        statusBox.setBackground(BG_CARD);
        statusBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(10, 16, 10, 16)
        ));

        JPanel leftStatus = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftStatus.setBackground(BG_CARD);

        pop3StatusDot = new JLabel("●");
        pop3StatusDot.setFont(new Font("Segoe UI", Font.BOLD, 12));
        pop3StatusDot.setForeground(GREEN_PRIMARY);

        pop3StatusHost = new JLabel("pop.gmail.com:995 · SSL");
        pop3StatusHost.setFont(new Font("Consolas", Font.BOLD, 12));
        pop3StatusHost.setForeground(TEXT_PRIMARY);

        JLabel lblConnected = new JLabel("Đã kết nối");
        lblConnected.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblConnected.setForeground(GREEN_SECONDARY);

        JPanel hostBox = new JPanel(new GridLayout(2, 1, 0, 2));
        hostBox.setBackground(BG_CARD);
        hostBox.add(pop3StatusHost);
        hostBox.add(lblConnected);

        leftStatus.add(pop3StatusDot);
        leftStatus.add(hostBox);

        pop3StatusStats = new JLabel("STAT: 12 thư · 43.2 KB");
        pop3StatusStats.setFont(new Font("Consolas", Font.PLAIN, 11));
        pop3StatusStats.setForeground(TEXT_MUTED);

        statusBox.add(leftStatus, BorderLayout.WEST);
        statusBox.add(pop3StatusStats, BorderLayout.EAST);

        // Danh sách email cuộn
        emailListContainer = new JPanel();
        emailListContainer.setLayout(new BoxLayout(emailListContainer, BoxLayout.Y_AXIS));
        emailListContainer.setBackground(BG_DARKEST);

        JScrollPane scrollPane = new JScrollPane(emailListContainer);
        scrollPane.setBackground(BG_DARKEST);
        scrollPane.getViewport().setBackground(BG_DARKEST);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // Nút to ở dưới: ↓ RETR · Tải thư mới
        btnFetchMail = createPrimaryButton("↓ RETR · Tải thư mới", false);
        btnFetchMail.addActionListener(e -> fetchEmailsAction());

        JPanel bottomBtnWrapper = new JPanel(new BorderLayout());
        bottomBtnWrapper.setBackground(BG_DARKEST);
        bottomBtnWrapper.setBorder(new EmptyBorder(12, 20, 12, 20));
        bottomBtnWrapper.add(btnFetchMail, BorderLayout.CENTER);

        // Tổ chức bố cục
        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.setBackground(BG_DARKEST);
        topContainer.add(headerPanel, BorderLayout.NORTH);
        topContainer.add(statusBox, BorderLayout.SOUTH);

        panel.add(topContainer, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(bottomBtnWrapper, BorderLayout.SOUTH);

        return panel;
    }

    // =========================================================================
    // 3. TAB 2: SOẠN THƯ (SMTP CLIENT) + THƯ ĐÃ GỬI
    // =========================================================================
    private JPanel createComposePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARKEST);

        // Header with toggle buttons
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(BG_DARKEST);
        headerPanel.setBorder(new EmptyBorder(10, 20, 10, 20));

        JPanel titleRow = new JPanel(new GridLayout(2, 1, 0, 4));
        titleRow.setBackground(BG_DARKEST);
        JLabel title = new JLabel("Soạn thư");
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(TEXT_PRIMARY);
        JLabel subTitle = new JLabel("Gửi email qua SMTP");
        subTitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subTitle.setForeground(TEXT_MUTED);
        titleRow.add(title);
        titleRow.add(subTitle);

        // Toggle: Soạn thư | Thư đã gửi
        JPanel toggleBar = new JPanel(new GridLayout(1, 2, 4, 0));
        toggleBar.setBackground(BG_DARKEST);
        toggleBar.setPreferredSize(new Dimension(0, 34));

        JButton btnToggleCompose = createFilterPill("✎ Soạn thư", true);
        JButton btnToggleSent = createFilterPill("📤 Đã gửi", false);

        // CardLayout để switch giữa form soạn và danh sách đã gửi
        CardLayout composeCard = new CardLayout();
        JPanel composeCardPanel = new JPanel(composeCard);
        composeCardPanel.setBackground(BG_DARKEST);

        // === CARD 1: FORM SOẠN THƯ ===
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBackground(BG_DARKEST);
        formPanel.setBorder(new EmptyBorder(0, 20, 0, 20));

        txtComposeFrom = createInputField("sinhvien@gmail.com");
        txtComposeTo = createInputField("giangvien@edu.vn");
        txtComposeSubject = createInputField("Bài thực hành SMTP");

        formPanel.add(createFormRow("Từ:", txtComposeFrom));
        formPanel.add(Box.createVerticalStrut(10));
        formPanel.add(createFormRow("Đến:", txtComposeTo));
        formPanel.add(Box.createVerticalStrut(10));
        formPanel.add(createFormRow("Chủ đề:", txtComposeSubject));
        formPanel.add(Box.createVerticalStrut(12));

        txtComposeBody = new JTextArea(8, 20);
        txtComposeBody.setText("Kính gửi thầy,\n\nEm xin nộp bài thực hành giao thức SMTP.\n\nEm cảm ơn.");
        txtComposeBody.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtComposeBody.setBackground(BG_INPUT);
        txtComposeBody.setForeground(TEXT_PRIMARY);
        txtComposeBody.setCaretColor(GREEN_PRIMARY);
        txtComposeBody.setLineWrap(true);
        txtComposeBody.setWrapStyleWord(true);
        txtComposeBody.setBorder(new EmptyBorder(10, 12, 10, 12));

        JScrollPane bodyScroll = new JScrollPane(txtComposeBody);
        bodyScroll.setBackground(BG_INPUT);
        bodyScroll.getViewport().setBackground(BG_INPUT);
        bodyScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        formPanel.add(bodyScroll);
        formPanel.add(Box.createVerticalStrut(14));

        // SMTP Status Bar
        JPanel smtpBar = new JPanel(new BorderLayout());
        smtpBar.setBackground(BG_CARD);
        smtpBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(10, 14, 10, 14)
        ));
        smtpStatusHost = new JLabel("🖧  smtp.gmail.com:587 · STARTTLS");
        smtpStatusHost.setFont(new Font("Consolas", Font.PLAIN, 12));
        smtpStatusHost.setForeground(GREEN_SECONDARY);
        JLabel arrowIcon = new JLabel("›");
        arrowIcon.setFont(new Font("Segoe UI", Font.BOLD, 16));
        arrowIcon.setForeground(TEXT_SUBTLE);
        smtpBar.add(smtpStatusHost, BorderLayout.WEST);
        smtpBar.add(arrowIcon, BorderLayout.EAST);
        formPanel.add(smtpBar);

        // === CARD 2: DANH SÁCH THƯ ĐÃ GỬI ===
        sentEmailListContainer = new JPanel();
        sentEmailListContainer.setLayout(new BoxLayout(sentEmailListContainer, BoxLayout.Y_AXIS));
        sentEmailListContainer.setBackground(BG_DARKEST);

        JScrollPane sentScroll = new JScrollPane(sentEmailListContainer);
        sentScroll.setBackground(BG_DARKEST);
        sentScroll.getViewport().setBackground(BG_DARKEST);
        sentScroll.setBorder(null);
        sentScroll.getVerticalScrollBar().setUnitIncrement(16);

        composeCardPanel.add(formPanel, "COMPOSE_FORM");
        composeCardPanel.add(sentScroll, "SENT_LIST");

        // Toggle button logic
        btnToggleCompose.addActionListener(e -> {
            showingCompose = true;
            composeCard.show(composeCardPanel, "COMPOSE_FORM");
            setFilterPillActive(btnToggleCompose, true);
            setFilterPillActive(btnToggleSent, false);
            btnSendData.setVisible(true);
        });
        btnToggleSent.addActionListener(e -> {
            showingCompose = false;
            composeCard.show(composeCardPanel, "SENT_LIST");
            setFilterPillActive(btnToggleCompose, false);
            setFilterPillActive(btnToggleSent, true);
            btnSendData.setVisible(false);
            loadSentEmailsToView();
        });

        toggleBar.add(btnToggleCompose);
        toggleBar.add(btnToggleSent);

        JPanel headerTop = new JPanel(new BorderLayout(0, 8));
        headerTop.setBackground(BG_DARKEST);
        headerTop.add(titleRow, BorderLayout.NORTH);
        headerTop.add(toggleBar, BorderLayout.SOUTH);
        headerPanel.add(headerTop, BorderLayout.CENTER);

        // Nút to ở dưới: ✈ DATA · Gửi thư
        btnSendData = createPrimaryButton("✈ DATA · Gửi thư", true);
        btnSendData.addActionListener(e -> sendEmailAction());

        JPanel bottomBtnWrapper = new JPanel(new BorderLayout());
        bottomBtnWrapper.setBackground(BG_DARKEST);
        bottomBtnWrapper.setBorder(new EmptyBorder(12, 20, 12, 20));
        bottomBtnWrapper.add(btnSendData, BorderLayout.CENTER);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(composeCardPanel, BorderLayout.CENTER);
        panel.add(bottomBtnWrapper, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Nạp danh sách thư đã gửi từ MySQL và hiển thị vào sentEmailListContainer
     */
    private void loadSentEmailsToView() {
        sentEmailListContainer.removeAll();
        List<EmailModel> sentList = DatabaseManager.loadSentEmails();
        if (sentList.isEmpty()) {
            JLabel empty = new JLabel("Chưa có thư đã gửi nào được lưu trong MySQL");
            empty.setForeground(TEXT_MUTED);
            empty.setFont(new Font("Segoe UI", Font.ITALIC, 13));
            empty.setBorder(new EmptyBorder(20, 20, 20, 20));
            sentEmailListContainer.add(empty);
        } else {
            for (EmailModel mail : sentList) {
                sentEmailListContainer.add(createSentEmailItem(mail));
                sentEmailListContainer.add(createDivider());
            }
        }
        sentEmailListContainer.revalidate();
        sentEmailListContainer.repaint();
    }

    private JPanel createSentEmailItem(EmailModel mail) {
        JPanel item = new JPanel(new BorderLayout(10, 0));
        item.setBackground(BG_DARKEST);
        item.setBorder(new EmptyBorder(10, 18, 10, 18));
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

        JLabel dot = new JLabel("➤");
        dot.setFont(new Font("Segoe UI", Font.BOLD, 11));
        dot.setForeground(GREEN_SECONDARY);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG_DARKEST);

        JLabel toLabel = new JLabel("Đến: " + mail.getTo());
        toLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        toLabel.setForeground(TEXT_PRIMARY);

        JLabel subjectLabel = new JLabel(mail.getSubject());
        subjectLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subjectLabel.setForeground(TEXT_MUTED);

        JLabel preview = new JLabel(mail.getPreview());
        preview.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        preview.setForeground(TEXT_SUBTLE);

        content.add(toLabel);
        content.add(Box.createVerticalStrut(2));
        content.add(subjectLabel);
        content.add(Box.createVerticalStrut(2));
        content.add(preview);

        JLabel timeLabel = new JLabel(mail.getDate());
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        timeLabel.setForeground(TEXT_MUTED);

        item.add(dot, BorderLayout.WEST);
        item.add(content, BorderLayout.CENTER);
        item.add(timeLabel, BorderLayout.EAST);

        item.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { item.setBackground(BG_CARD); content.setBackground(BG_CARD); }
            @Override
            public void mouseExited(MouseEvent e) { item.setBackground(BG_DARKEST); content.setBackground(BG_DARKEST); }
            @Override
            public void mouseClicked(MouseEvent e) { showEmailDetailDialog(mail); }
        });
        return item;
    }

    private JPanel createFormRow(String labelText, JComponent inputField) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(BG_DARKEST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        label.setForeground(TEXT_MUTED);
        label.setPreferredSize(new Dimension(60, 36));

        row.add(label, BorderLayout.WEST);
        row.add(inputField, BorderLayout.CENTER);
        return row;
    }

    // =========================================================================
    // 4. TAB 3: NHẬT KÝ GIAO THỨC (PROTOCOL LOGS)
    // =========================================================================
    private JPanel createLogsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARKEST);

        // Header: Filter buttons
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(BG_DARKEST);
        headerPanel.setBorder(new EmptyBorder(10, 20, 10, 20));

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterBar.setBackground(BG_DARKEST);

        btnLogFilterAll = createFilterPill("Tất cả", true);
        btnLogFilterSmtp = createFilterPill("SMTP", false);
        btnLogFilterPop3 = createFilterPill("POP3", false);

        btnLogFilterAll.addActionListener(e -> setLogFilter("ALL"));
        btnLogFilterSmtp.addActionListener(e -> setLogFilter("SMTP"));
        btnLogFilterPop3.addActionListener(e -> setLogFilter("POP3"));

        filterBar.add(btnLogFilterAll);
        filterBar.add(btnLogFilterSmtp);
        filterBar.add(btnLogFilterPop3);

        JButton btnClear = createRoundIconButton("🗑");
        btnClear.addActionListener(e -> ProtocolLogger.getInstance().clear());

        headerPanel.add(filterBar, BorderLayout.WEST);
        headerPanel.add(btnClear, BorderLayout.EAST);

        // Terminal Log Area
        logTextPane = new JTextPane();
        logTextPane.setEditable(false);
        logTextPane.setBackground(BG_DARKEST);
        logTextPane.setFont(new Font("Consolas", Font.PLAIN, 12));
        logTextPane.setBorder(new EmptyBorder(10, 14, 10, 14));
        logDoc = logTextPane.getStyledDocument();

        JScrollPane logScroll = new JScrollPane(logTextPane);
        logScroll.setBackground(BG_DARKEST);
        logScroll.getViewport().setBackground(BG_DARKEST);
        logScroll.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(0, 16, 0, 16),
                BorderFactory.createLineBorder(BORDER_COLOR, 1)
        ));
        logScroll.getVerticalScrollBar().setUnitIncrement(16);

        // Bottom Input bar: > NOOP & Send button
        JPanel inputBar = new JPanel(new BorderLayout(8, 0));
        inputBar.setBackground(BG_DARKEST);
        inputBar.setBorder(new EmptyBorder(12, 16, 12, 16));

        txtLogInput = createInputField("NOOP");
        txtLogInput.setFont(new Font("Consolas", Font.PLAIN, 13));

        JButton btnSendCmd = new JButton("↑");
        btnSendCmd.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btnSendCmd.setBackground(GREEN_PRIMARY);
        btnSendCmd.setForeground(BG_DARKEST);
        btnSendCmd.setFocusPainted(false);
        btnSendCmd.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        btnSendCmd.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSendCmd.addActionListener(e -> executeRawCommand());

        txtLogInput.addActionListener(e -> executeRawCommand());

        inputBar.add(txtLogInput, BorderLayout.CENTER);
        inputBar.add(btnSendCmd, BorderLayout.EAST);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(logScroll, BorderLayout.CENTER);
        panel.add(inputBar, BorderLayout.SOUTH);

        return panel;
    }

    private JButton createFilterPill(String text, boolean active) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        setFilterPillActive(btn, active);
        return btn;
    }

    private void setFilterPillActive(JButton btn, boolean active) {
        if (active) {
            btn.setBackground(GREEN_PRIMARY);
            btn.setForeground(BG_DARKEST);
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(GREEN_PRIMARY, 1),
                    new EmptyBorder(5, 14, 5, 14)
            ));
        } else {
            btn.setBackground(BG_CARD);
            btn.setForeground(TEXT_MUTED);
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_COLOR, 1),
                    new EmptyBorder(5, 14, 5, 14)
            ));
        }
    }

    private void setLogFilter(String filter) {
        currentLogFilter = filter;
        setFilterPillActive(btnLogFilterAll, "ALL".equals(filter));
        setFilterPillActive(btnLogFilterSmtp, "SMTP".equals(filter));
        setFilterPillActive(btnLogFilterPop3, "POP3".equals(filter));
        reloadLogsToView();
    }

    // =========================================================================
    // 5. TAB 4: TÀI KHOẢN (ACCOUNT SETTINGS)
    // =========================================================================
    private JPanel createAccountPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARKEST);

        // Header
        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        headerPanel.setBackground(BG_DARKEST);
        headerPanel.setBorder(new EmptyBorder(10, 20, 14, 20));

        JLabel title = new JLabel("Tài khoản");
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(TEXT_PRIMARY);

        JLabel subTitle = new JLabel("Cấu hình máy chủ mail của bạn");
        subTitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subTitle.setForeground(TEXT_MUTED);

        headerPanel.add(title);
        headerPanel.add(subTitle);

        // Center scrollable settings
        JPanel settingsList = new JPanel();
        settingsList.setLayout(new BoxLayout(settingsList, BoxLayout.Y_AXIS));
        settingsList.setBackground(BG_DARKEST);
        settingsList.setBorder(new EmptyBorder(0, 20, 0, 20));

        // Group 1: POP3 (NHẬN THƯ)
        JPanel pop3Group = createSettingsGroup("POP3 (NHẬN THƯ)");
        txtPop3Server = createSmallField("pop.gmail.com");
        txtPop3Port = createSmallField("995");
        pop3Group.add(createSettingRow("Server", txtPop3Server));
        pop3Group.add(createDivider());
        pop3Group.add(createSettingRow("Cổng", txtPop3Port));
        pop3Group.add(createDivider());
        pop3Group.add(createSettingRowWithText("Bảo mật", "SSL/TLS ›"));
        settingsList.add(pop3Group);
        settingsList.add(Box.createVerticalStrut(14));

        // Group 2: SMTP (GỬI THƯ)
        JPanel smtpGroup = createSettingsGroup("SMTP (GỬI THƯ)");
        txtSmtpServer = createSmallField("smtp.gmail.com");
        txtSmtpPort = createSmallField("587");
        smtpGroup.add(createSettingRow("Server", txtSmtpServer));
        smtpGroup.add(createDivider());
        smtpGroup.add(createSettingRow("Cổng", txtSmtpPort));
        smtpGroup.add(createDivider());
        smtpGroup.add(createSettingRowWithText("Bảo mật", "STARTTLS ›"));
        settingsList.add(smtpGroup);
        settingsList.add(Box.createVerticalStrut(14));

        // Group 3: THÔNG TIN ĐĂNG NHẬP
        JPanel authGroup = createSettingsGroup("THÔNG TIN ĐĂNG NHẬP");
        txtAccountEmail = createInputField("sinhvien@gmail.com");
        txtAccountPassword = new JPasswordField();
        txtAccountPassword.setBackground(BG_INPUT);
        txtAccountPassword.setForeground(TEXT_PRIMARY);
        txtAccountPassword.setCaretColor(GREEN_PRIMARY);
        txtAccountPassword.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtAccountPassword.setBorder(new EmptyBorder(8, 12, 8, 12));

        authGroup.add(createIconInputRow("✉", txtAccountEmail));
        authGroup.add(Box.createVerticalStrut(8));
        authGroup.add(createIconInputRow("🔒", txtAccountPassword));

        JLabel noteLabel = new JLabel("<html><i>Lưu ý: Dùng Mật khẩu ứng dụng (App Password) nếu dùng Gmail</i></html>");
        noteLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        noteLabel.setForeground(TEXT_MUTED);
        noteLabel.setBorder(new EmptyBorder(6, 4, 2, 4));
        authGroup.add(noteLabel);

        settingsList.add(authGroup);

        // Group 4: CƠ SỞ DỮ LIỆU (MYSQL)
        JPanel dbGroup = createSettingsGroup("CƠ SỞ DỮ LIỆU (MYSQL)");
        dbGroup.add(createSettingRowWithText("Máy chủ", "localhost:3306"));
        dbGroup.add(createDivider());
        dbGroup.add(createSettingRowWithText("Database", "mail_client_db"));
        dbGroup.add(createDivider());

        // Label trạng thái DB - cập nhật được
        lblDbStatus = new JLabel(getDbStatusText());
        lblDbStatus.setFont(new Font("Consolas", Font.PLAIN, 13));
        lblDbStatus.setForeground(DatabaseManager.isConnected() ? GREEN_SECONDARY : DANGER_RED);

        JPanel dbStatusRow = new JPanel(new BorderLayout());
        dbStatusRow.setBackground(BG_CARD);
        dbStatusRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        JLabel lblDbLabel = new JLabel("Trạng thái");
        lblDbLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblDbLabel.setForeground(TEXT_MUTED);
        dbStatusRow.add(lblDbLabel, BorderLayout.WEST);
        dbStatusRow.add(lblDbStatus, BorderLayout.EAST);
        dbGroup.add(dbStatusRow);
        dbGroup.add(createDivider());

        // Nút kết nối lại DB
        JButton btnReconnectDb = new JButton("🔄 Kết nối lại MySQL");
        btnReconnectDb.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnReconnectDb.setBackground(BG_INPUT);
        btnReconnectDb.setForeground(GREEN_SECONDARY);
        btnReconnectDb.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(5, 10, 5, 10)
        ));
        btnReconnectDb.setFocusPainted(false);
        btnReconnectDb.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnReconnectDb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        btnReconnectDb.addActionListener(e -> {
            btnReconnectDb.setText("Đang kết nối...");
            btnReconnectDb.setEnabled(false);
            new Thread(() -> {
                boolean ok = DatabaseManager.tryConnect();
                SwingUtilities.invokeLater(() -> {
                    lblDbStatus.setText(getDbStatusText());
                    lblDbStatus.setForeground(ok ? GREEN_SECONDARY : DANGER_RED);
                    btnReconnectDb.setText("🔄 Kết nối lại MySQL");
                    btnReconnectDb.setEnabled(true);
                    String msg = ok
                            ? "Kết nối MySQL thành công!\nDatabase: mail_client_db"
                            : "Không kết nối được MySQL!\n" + DatabaseManager.getLastError()
                            + "\n\nKiểm tra:\n• MySQL Service đang chạy chưa?\n• XAMPP -> Start MySQL";
                    JOptionPane.showMessageDialog(MailAppGUI.this, msg,
                            ok ? "Thành công" : "Lỗi MySQL",
                            ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                });
            }).start();
        });
        dbGroup.add(btnReconnectDb);

        settingsList.add(Box.createVerticalStrut(14));
        settingsList.add(dbGroup);

        JScrollPane scrollPane = new JScrollPane(settingsList);
        scrollPane.setBackground(BG_DARKEST);
        scrollPane.getViewport().setBackground(BG_DARKEST);
        scrollPane.setBorder(null);

        // Bottom Button: Kiểm tra kết nối
        btnCheckConnection = createPrimaryButton("Kiểm tra kết nối", true);
        btnCheckConnection.addActionListener(e -> checkConnectionAction());

        JPanel bottomBtnWrapper = new JPanel(new BorderLayout());
        bottomBtnWrapper.setBackground(BG_DARKEST);
        bottomBtnWrapper.setBorder(new EmptyBorder(12, 20, 12, 20));
        bottomBtnWrapper.add(btnCheckConnection, BorderLayout.CENTER);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(bottomBtnWrapper, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createSettingsGroup(String title) {
        JPanel group = new JPanel();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setBackground(BG_CARD);
        group.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(12, 14, 12, 14)
        ));

        JLabel groupTitle = new JLabel(title);
        groupTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        groupTitle.setForeground(TEXT_MUTED);
        groupTitle.setBorder(new EmptyBorder(0, 0, 8, 0));

        group.add(groupTitle);
        return group;
    }

    private JPanel createSettingRow(String labelText, JComponent field) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(BG_CARD);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        label.setForeground(TEXT_MUTED);

        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.EAST);
        return row;
    }

    private JPanel createSettingRowWithText(String labelText, String valueText) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(BG_CARD);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        label.setForeground(TEXT_MUTED);

        JLabel val = new JLabel(valueText);
        val.setFont(new Font("Consolas", Font.PLAIN, 13));
        val.setForeground(GREEN_SECONDARY);

        row.add(label, BorderLayout.WEST);
        row.add(val, BorderLayout.EAST);
        return row;
    }

    private JPanel createIconInputRow(String icon, JComponent field) {
        JPanel box = new JPanel(new BorderLayout(8, 0));
        box.setBackground(BG_INPUT);
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(2, 8, 2, 8)
        ));
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel ic = new JLabel(icon);
        ic.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        ic.setForeground(GREEN_SECONDARY);

        box.add(ic, BorderLayout.WEST);
        box.add(field, BorderLayout.CENTER);
        return box;
    }

    private JSeparator createDivider() {
        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER_COLOR);
        sep.setBackground(BORDER_COLOR);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    // =========================================================================
    // 6. BOTTOM NAVIGATION BAR
    // =========================================================================
    private JPanel createBottomNav() {
        JPanel nav = new JPanel(new GridLayout(1, 4, 0, 0));
        nav.setBackground(BG_CARD);
        nav.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR));
        nav.setPreferredSize(new Dimension(0, 60));

        btnNavInbox = createNavButton("✉", "Hộp thư", "INBOX");
        btnNavCompose = createNavButton("✎", "Soạn thư", "COMPOSE");
        btnNavLogs = createNavButton("📜", "Nhật ký", "LOGS");
        btnNavAccount = createNavButton("👤", "Tài khoản", "ACCOUNT");

        nav.add(btnNavInbox);
        nav.add(btnNavCompose);
        nav.add(btnNavLogs);
        nav.add(btnNavAccount);

        return nav;
    }

    private JButton createNavButton(String icon, String label, String tabKey) {
        JButton btn = new JButton("<html><center>" + icon + "<br><small>" + label + "</small></center></html>");
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setBackground(BG_CARD);
        btn.setForeground(TEXT_MUTED);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> switchTab(tabKey));
        return btn;
    }

    private void switchTab(String tabKey) {
        currentTab = tabKey;
        cardLayout.show(contentCards, tabKey);

        // Highlight icon
        updateNavButtonState(btnNavInbox, "INBOX".equals(tabKey));
        updateNavButtonState(btnNavCompose, "COMPOSE".equals(tabKey));
        updateNavButtonState(btnNavLogs, "LOGS".equals(tabKey));
        updateNavButtonState(btnNavAccount, "ACCOUNT".equals(tabKey));

        // Cập nhật email người gửi vào ô "Từ" của soạn thư
        if ("COMPOSE".equals(tabKey)) {
            String accEmail = txtAccountEmail.getText().trim();
            if (!accEmail.isEmpty()) {
                txtComposeFrom.setText(accEmail);
            }
        }
    }

    private void updateNavButtonState(JButton btn, boolean active) {
        if (btn == null) return;
        btn.setForeground(active ? GREEN_PRIMARY : TEXT_MUTED);
        btn.setFont(new Font("Segoe UI", active ? Font.BOLD : Font.PLAIN, 12));
    }

    // =========================================================================
    // 7. XỬ LÝ SỰ KIỆN GIAO THỨC (SMTP & POP3)
    // =========================================================================

    /**
     * Tải thư mới qua giao thức POP3
     */
    private void fetchEmailsAction() {
        btnFetchMail.setEnabled(false);
        btnFetchMail.setText("Đang kết nối POP3...");

        new Thread(() -> {
            String server = txtPop3Server.getText().trim();
            int port = Integer.parseInt(txtPop3Port.getText().trim());
            String email = txtAccountEmail.getText().trim();
            String password = new String(txtAccountPassword.getPassword()).trim();

            try {
                if (email.isEmpty() || password.isEmpty()) {
                    // Nếu chưa có tài khoản, hiển thị thông báo và giả lập log để test
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this,
                                "Vui lòng nhập Email và Mật khẩu ở tab 'Tài khoản'!\n(Nếu dùng Gmail cần tạo App Password)",
                                "Chưa cấu hình tài khoản", JOptionPane.WARNING_MESSAGE);
                        switchTab("ACCOUNT");
                    });
                    return;
                }

                Pop3Client client = new Pop3Client(server, port);
                client.connect(email, password);

                Pop3Client.StatResult stat = client.stat();
                List<String> listItems = client.list();

                List<EmailModel> fetchedList = new ArrayList<>();
                int totalToFetch = Math.min(stat.count, 15);
                for (int i = stat.count; i > stat.count - totalToFetch && i > 0; i--) {
                    EmailModel mail = client.top(i, 50);
                    fetchedList.add(mail);
                }

                client.close();

                // Lưu vào cơ sở dữ liệu MySQL
                try {
                    DatabaseManager.saveInboxEmails(fetchedList);
                } catch (Exception ignored) {}

                SwingUtilities.invokeLater(() -> {
                    currentEmails.clear();
                    currentEmails.addAll(fetchedList);
                    renderEmailList();

                    double sizeKb = Math.round(stat.size / 102.4) / 10.0;
                    pop3StatusStats.setText("STAT: " + stat.count + " thư · " + sizeKb + " KB");
                    pop3StatusDot.setForeground(GREEN_PRIMARY);
                    JOptionPane.showMessageDialog(this, "Đã tải " + fetchedList.size() + " thư thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                });

            } catch (Exception ex) {
                ProtocolLogger.getInstance().log("POP3", "S", "LỖI: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    pop3StatusDot.setForeground(DANGER_RED);
                    JOptionPane.showMessageDialog(this,
                            "Lỗi POP3: " + ex.getMessage() + "\nXem chi tiết tại tab 'Nhật ký'",
                            "Lỗi kết nối POP3", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    btnFetchMail.setEnabled(true);
                    btnFetchMail.setText("↓ RETR · Tải thư mới");
                });
            }
        }).start();
    }

    /**
     * Gửi email qua giao thức SMTP
     */
    private void sendEmailAction() {
        String from = txtComposeFrom.getText().trim();
        String to = txtComposeTo.getText().trim();
        String subject = txtComposeSubject.getText().trim();
        String body = txtComposeBody.getText();

        if (from.isEmpty() || to.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập người gửi (Từ) và người nhận (Đến)!", "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnSendData.setEnabled(false);
        btnSendData.setText("Đang gửi qua SMTP...");

        new Thread(() -> {
            String server = txtSmtpServer.getText().trim();
            int port = Integer.parseInt(txtSmtpPort.getText().trim());
            String password = new String(txtAccountPassword.getPassword()).trim();

            try {
                if (password.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this,
                                "Vui lòng nhập Mật khẩu tại tab 'Tài khoản' để xác thực AUTH LOGIN!",
                                "Cần mật khẩu", JOptionPane.WARNING_MESSAGE);
                        switchTab("ACCOUNT");
                    });
                    return;
                }

                SmtpClient client = new SmtpClient(server, port);
                client.connect(from, password);
                client.sendEmail(from, to, subject, body);

                // Lưu vào cơ sở dữ liệu MySQL
                try {
                    DatabaseManager.saveSentEmail(from, to, subject, body);
                    // Lưu thêm thông tin tài khoản
                    String smtpServerFinal = txtSmtpServer.getText().trim();
                    int smtpPortFinal = Integer.parseInt(txtSmtpPort.getText().trim());
                    String popServerFinal = txtPop3Server.getText().trim();
                    int popPortFinal = Integer.parseInt(txtPop3Port.getText().trim());
                    DatabaseManager.saveAccount(from, password, popServerFinal, popPortFinal, smtpServerFinal, smtpPortFinal);
                } catch (Exception dbEx) {
                    System.err.println("[DB] Lỗi lưu thư đã gửi: " + dbEx.getMessage());
                    dbEx.printStackTrace();
                }

                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Đã gửi email thành công tới " + to + "!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                    txtComposeSubject.setText("");
                    txtComposeBody.setText("");
                });

            } catch (Exception ex) {
                ProtocolLogger.getInstance().log("SMTP", "S", "LỖI: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Lỗi SMTP: " + ex.getMessage() + "\nXem chi tiết tại tab 'Nhật ký'",
                            "Gửi thư thất bại", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    btnSendData.setEnabled(true);
                    btnSendData.setText("✈ DATA · Gửi thư");
                });
            }
        }).start();
    }

    /**
     * Kiểm tra kết nối cả POP3 và SMTP
     */
    private void checkConnectionAction() {
        btnCheckConnection.setEnabled(false);
        btnCheckConnection.setText("Đang kiểm tra kết nối...");

        new Thread(() -> {
            String popServer = txtPop3Server.getText().trim();
            int popPort = Integer.parseInt(txtPop3Port.getText().trim());
            String smtpServer = txtSmtpServer.getText().trim();
            int smtpPort = Integer.parseInt(txtSmtpPort.getText().trim());
            String email = txtAccountEmail.getText().trim();
            String password = new String(txtAccountPassword.getPassword()).trim();

            StringBuilder report = new StringBuilder();
            boolean popOk = false;
            boolean smtpOk = false;

            // 1. Check POP3
            try {
                Pop3Client pop = new Pop3Client(popServer, popPort);
                pop.connect(email, password);
                Pop3Client.StatResult stat = pop.stat();
                pop.close();
                popOk = true;
                report.append("✔ POP3: Thành công (").append(stat.count).append(" thư)\n");
            } catch (Exception e) {
                report.append("✖ POP3: ").append(e.getMessage()).append("\n");
            }

            // 2. Check SMTP
            try {
                SmtpClient smtp = new SmtpClient(smtpServer, smtpPort);
                smtp.connect(email, password);
                smtp.noop();
                smtp.close();
                smtpOk = true;
                report.append("✔ SMTP: Thành công (Kết nối & NOOP OK)\n");
            } catch (Exception e) {
                report.append("✖ SMTP: ").append(e.getMessage()).append("\n");
            }

            final boolean finalPopOk = popOk;
            final boolean finalSmtpOk = smtpOk;

            // Lưu cấu hình tài khoản vào MySQL nếu kết nối thành công hoặc có thông tin
            try {
                DatabaseManager.saveAccount(email, password, popServer, popPort, smtpServer, smtpPort);
            } catch (Exception ignored) {}

            SwingUtilities.invokeLater(() -> {
                btnCheckConnection.setEnabled(true);
                btnCheckConnection.setText("Kiểm tra kết nối");

                pop3StatusDot.setForeground(finalPopOk ? GREEN_PRIMARY : DANGER_RED);
                pop3StatusHost.setText(popServer + ":" + popPort + " · SSL");
                smtpStatusHost.setText("🖧  " + smtpServer + ":" + smtpPort + " · STARTTLS");

                int msgType = (finalPopOk && finalSmtpOk) ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE;
                JOptionPane.showMessageDialog(this, report.toString(), "Kết quả kiểm tra", msgType);
            });
        }).start();
    }

    /**
     * Gửi lệnh NOOP trực tiếp từ tab Nhật ký
     */
    private void executeRawCommand() {
        String cmd = txtLogInput.getText().trim();
        if (cmd.isEmpty()) return;

        txtLogInput.setText("");
        new Thread(() -> {
            String server = txtSmtpServer.getText().trim();
            int port = Integer.parseInt(txtSmtpPort.getText().trim());
            String email = txtAccountEmail.getText().trim();
            String password = new String(txtAccountPassword.getPassword()).trim();

            try {
                if (password.isEmpty() || email.isEmpty()) {
                    ProtocolLogger.getInstance().log("SMTP", "C", cmd);
                    ProtocolLogger.getInstance().log("SMTP", "S", "250 2.0.0 OK (Mô phỏng - Vui lòng nhập tài khoản để gửi lệnh thật)");
                    return;
                }

                SmtpClient smtp = new SmtpClient(server, port);
                smtp.connect(email, password);
                smtp.noop();
                smtp.close();

            } catch (Exception ex) {
                ProtocolLogger.getInstance().log("SMTP", "S", "Lỗi: " + ex.getMessage());
            }
        }).start();
    }

    // =========================================================================
    // 8. LOGIC GIAO DIỆN & SAMPLE DATA
    // =========================================================================
    private void setupProtocolLogListener() {
        ProtocolLogger.getInstance().addListener(new ProtocolLogger.LogListener() {
            @Override
            public void onNewLog(ProtocolLogEntry entry) {
                SwingUtilities.invokeLater(() -> {
                    if ("ALL".equalsIgnoreCase(currentLogFilter) || entry.getType().equalsIgnoreCase(currentLogFilter)) {
                        appendLogEntryToDoc(entry);
                    }
                });
            }

            @Override
            public void onLogsCleared() {
                SwingUtilities.invokeLater(() -> logTextPane.setText(""));
            }
        });
    }

    private void reloadLogsToView() {
        logTextPane.setText("");
        List<ProtocolLogEntry> logs = ProtocolLogger.getInstance().getLogsByType(currentLogFilter);
        for (ProtocolLogEntry entry : logs) {
            appendLogEntryToDoc(entry);
        }
    }

    private void appendLogEntryToDoc(ProtocolLogEntry entry) {
        try {
            StyleContext sc = StyleContext.getDefaultStyleContext();

            // Style thời gian (xám)
            AttributeSet timeStyle = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, TEXT_MUTED);
            // Style Server (xanh lá neon)
            AttributeSet serverStyle = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, GREEN_PRIMARY);
            // Style Client (trắng)
            AttributeSet clientStyle = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, TEXT_PRIMARY);

            logDoc.insertString(logDoc.getLength(), entry.getTimestamp() + " ", timeStyle);

            if ("S".equals(entry.getDirection())) {
                logDoc.insertString(logDoc.getLength(), "S: " + entry.getMessage() + "\n", serverStyle);
            } else {
                logDoc.insertString(logDoc.getLength(), "C: " + entry.getMessage() + "\n", clientStyle);
            }

            // Tự động cuộn xuống cuối
            logTextPane.setCaretPosition(logDoc.getLength());

        } catch (BadLocationException ignored) {}
    }

    private void loadDataFromDatabaseOrSample() {
        // 1. Thử nạp tài khoản đã lưu trong MySQL
        try {
            DatabaseManager.AccountData acc = DatabaseManager.getLatestAccount();
            if (acc != null && acc.email != null && !acc.email.isEmpty()) {
                txtAccountEmail.setText(acc.email);
                txtAccountPassword.setText(acc.password != null ? acc.password : "");
                txtPop3Server.setText(acc.popServer != null ? acc.popServer : "pop.gmail.com");
                txtPop3Port.setText(String.valueOf(acc.popPort > 0 ? acc.popPort : 995));
                txtSmtpServer.setText(acc.smtpServer != null ? acc.smtpServer : "smtp.gmail.com");
                txtSmtpPort.setText(String.valueOf(acc.smtpPort > 0 ? acc.smtpPort : 587));
                txtComposeFrom.setText(acc.email);
            }
        } catch (Exception ignored) {}

        // 2. Thử nạp danh sách email từ MySQL
        try {
            List<EmailModel> dbEmails = DatabaseManager.loadInboxEmails();
            if (dbEmails != null && !dbEmails.isEmpty()) {
                currentEmails.clear();
                currentEmails.addAll(dbEmails);
                renderEmailList();
                pop3StatusStats.setText("STAT: " + currentEmails.size() + " thư (MySQL)");
                return;
            }
        } catch (Exception ignored) {}

        // 3. Nếu trong MySQL chưa có email, nạp dữ liệu mẫu ban đầu để giao diện luôn đẹp
        loadSampleData();
    }

    private void loadSampleData() {
        // Nạp các email mẫu giống ảnh thiết kế của người dùng
        currentEmails.add(new EmailModel(1, "Nguyễn Văn An", "an.nv@gmail.com", "sinhvien@gmail.com",
                "Báo cáo bài tập mạng máy tính", "09:41 >",
                "Em gửi kèm báo cáo bài tập như đã trao đổi trong buổi học tuần trước. Thầy xem qua giúp em với ạ.", 4096));

        currentEmails.add(new EmailModel(2, "Phòng Đào tạo", "daotao@edu.vn", "sinhvien@gmail.com",
                "Lịch thi học kỳ", "08:17 >",
                "Thông báo lịch thi học kỳ 2024 - 2025 cho toàn thể sinh viên các lớp lập trình mạng.", 2048));

        currentEmails.add(new EmailModel(3, "GitHub", "notifications@github.com", "sinhvien@gmail.com",
                "New sign-in", "07:32 >",
                "A new sign-in to your account was detected from Windows 11 Vietnam.", 1024));

        currentEmails.add(new EmailModel(4, "Trần Minh", "minh.tran@gmail.com", "sinhvien@gmail.com",
                "Re: Demo SMTP", "Hôm qua >",
                "Mình đã thử gửi mail qua SMTP, mọi thứ đã hoạt động trơn tru rồi nhé!", 3072));

        currentEmails.add(new EmailModel(5, "Lê Thị Hương", "huong.le@gmail.com", "sinhvien@gmail.com",
                "Tài liệu ôn tập", "Hôm qua >",
                "Gửi các bạn tài liệu ôn tập môn Mạng máy tính và Lập trình mạng.", 8192));

        currentEmails.add(new EmailModel(6, "Apple", "no-reply@apple.com", "sinhvien@gmail.com",
                "Your Apple ID", "Hôm qua >",
                "Your Apple ID was used to sign in to a new browser device.", 1500));

        currentEmails.add(new EmailModel(7, "Nguyễn Quang", "quang.ng@gmail.com", "sinhvien@gmail.com",
                "Hẹn gặp ngày mai", "Hôm qua >",
                "Mai mình gặp nhau để thảo luận dự án nhé!", 1200));

        renderEmailList();

        // Nạp mẫu một chuỗi log SMTP & POP3 như trong ảnh mockup
        ProtocolLogger log = ProtocolLogger.getInstance();
        log.log("SMTP", "S", "220 smtp.gmail.com ESMTP ready");
        log.log("SMTP", "C", "EHLO localhost");
        log.log("SMTP", "S", "250-smtp.gmail.com at your service");
        log.log("SMTP", "S", "250-PIPELINING");
        log.log("SMTP", "S", "250-SIZE 35882577");
        log.log("SMTP", "S", "250-STARTTLS");
        log.log("SMTP", "S", "250-AUTH LOGIN PLAIN XOAUTH2");
        log.log("SMTP", "S", "250 8BITMIME");
        log.log("SMTP", "C", "MAIL FROM:<sinhvien@gmail.com>");
        log.log("SMTP", "S", "250 2.1.0 OK");
        log.log("SMTP", "C", "RCPT TO:<giangvien@edu.vn>");
        log.log("SMTP", "S", "250 2.1.5 OK");
        log.log("SMTP", "C", "DATA");
        log.log("SMTP", "S", "354 End data with <CR><LF>.<CR><LF>");
        log.log("SMTP", "C", "Subject: Bai tap SMTP");
        log.log("SMTP", "C", "From: sinhvien@gmail.com");
        log.log("SMTP", "C", "To: giangvien@edu.vn");
        log.log("SMTP", "C", "Xin chao thay co, Day la bai tap ve SMTP.");
        log.log("SMTP", "C", ".");
        log.log("SMTP", "S", "250 2.0.0 OK: queued as 6F1A23B4C7");
    }

    private void renderEmailList() {
        emailListContainer.removeAll();
        for (EmailModel mail : currentEmails) {
            emailListContainer.add(createEmailItem(mail));
            emailListContainer.add(createDivider());
        }
        emailListContainer.revalidate();
        emailListContainer.repaint();
    }

    private JPanel createEmailItem(EmailModel mail) {
        JPanel item = new JPanel(new BorderLayout(10, 0));
        item.setBackground(BG_DARKEST);
        item.setBorder(new EmptyBorder(12, 18, 12, 18));
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 75));
        item.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Dấu chấm xanh bên trái
        JLabel dot = new JLabel("●");
        dot.setFont(new Font("Segoe UI", Font.BOLD, 10));
        dot.setForeground(GREEN_PRIMARY);

        // Nội dung chính
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG_DARKEST);

        JLabel sender = new JLabel(mail.getFromName());
        sender.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sender.setForeground(TEXT_PRIMARY);

        JLabel subject = new JLabel(mail.getSubject());
        subject.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subject.setForeground(TEXT_MUTED);

        JLabel preview = new JLabel(mail.getPreview());
        preview.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        preview.setForeground(TEXT_SUBTLE);

        content.add(sender);
        content.add(Box.createVerticalStrut(2));
        content.add(subject);
        content.add(Box.createVerticalStrut(2));
        content.add(preview);

        // Thời gian bên phải và nút xóa
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightPanel.setBackground(BG_DARKEST);

        JLabel time = new JLabel(mail.getDate());
        time.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        time.setForeground(TEXT_MUTED);

        JButton btnDelete = new JButton("🗑");
        btnDelete.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnDelete.setForeground(DANGER_RED);
        btnDelete.setBackground(BG_CARD);
        btnDelete.setFocusPainted(false);
        btnDelete.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        btnDelete.setPreferredSize(new Dimension(28, 28));
        btnDelete.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnDelete.setToolTipText("Xóa thư (Lệnh POP3 DELE)");
        btnDelete.addActionListener(e -> deleteEmailAction(mail, null));

        rightPanel.add(time);
        rightPanel.add(btnDelete);

        item.add(dot, BorderLayout.WEST);
        item.add(content, BorderLayout.CENTER);
        item.add(rightPanel, BorderLayout.EAST);

        // Hover effect
        item.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                item.setBackground(BG_CARD);
                content.setBackground(BG_CARD);
                rightPanel.setBackground(BG_CARD);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                item.setBackground(BG_DARKEST);
                content.setBackground(BG_DARKEST);
                rightPanel.setBackground(BG_DARKEST);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                showEmailDetailDialog(mail);
            }
        });

        return item;
    }

    private void showEmailDetailDialog(EmailModel mail) {
        JDialog dialog = new JDialog(this, "Chi tiết thư", true);
        dialog.setSize(420, 520);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(BG_DARKEST);
        dialog.setLayout(new BorderLayout());

        JPanel header = new JPanel(new GridLayout(4, 1, 0, 4));
        header.setBackground(BG_CARD);
        header.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel sub = new JLabel("Tiêu đề: " + mail.getSubject());
        sub.setFont(new Font("Segoe UI", Font.BOLD, 15));
        sub.setForeground(TEXT_PRIMARY);

        JLabel from = new JLabel("Từ: " + mail.getFromName() + " <" + mail.getFromEmail() + ">");
        from.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        from.setForeground(TEXT_MUTED);

        JLabel to = new JLabel("Đến: " + mail.getTo());
        to.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        to.setForeground(TEXT_MUTED);

        JLabel date = new JLabel("Ngày: " + mail.getDate());
        date.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        date.setForeground(GREEN_SECONDARY);

        header.add(sub);
        header.add(from);
        header.add(to);
        header.add(date);

        JTextArea bodyArea = new JTextArea(mail.getBody());
        bodyArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        bodyArea.setBackground(BG_DARKEST);
        bodyArea.setForeground(TEXT_PRIMARY);
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setEditable(false);
        bodyArea.setBorder(new EmptyBorder(14, 16, 14, 16));

        JScrollPane scroll = new JScrollPane(bodyArea);
        scroll.setBorder(null);

        // Nút bấm: Xóa thư (DELE) & Đóng
        JButton btnDelete = new JButton("🗑 DELE · Xóa thư");
        btnDelete.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnDelete.setBackground(new Color(60, 20, 20));
        btnDelete.setForeground(DANGER_RED);
        btnDelete.setBorder(BorderFactory.createLineBorder(DANGER_RED, 1));
        btnDelete.setPreferredSize(new Dimension(140, 42));
        btnDelete.setFocusPainted(false);
        btnDelete.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnDelete.addActionListener(e -> deleteEmailAction(mail, dialog));

        JButton btnClose = createPrimaryButton("Đóng", true);
        btnClose.setPreferredSize(new Dimension(100, 42));
        btnClose.addActionListener(e -> dialog.dispose());

        JPanel btm = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btm.setBackground(BG_DARKEST);
        btm.setBorder(new EmptyBorder(10, 16, 14, 16));
        btm.add(btnDelete);
        btm.add(btnClose);

        dialog.add(header, BorderLayout.NORTH);
        dialog.add(scroll, BorderLayout.CENTER);
        dialog.add(btm, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    /**
     * Xóa thư bằng lệnh POP3 DELE
     */
    private void deleteEmailAction(EmailModel mail, JDialog parentDialog) {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Bạn có chắc chắn muốn xóa thư:\n\"" + mail.getSubject() + "\"\n(Lệnh POP3 DELE trên server)?",
                "Xác nhận xóa thư",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        new Thread(() -> {
            String server = txtPop3Server.getText().trim();
            int port = Integer.parseInt(txtPop3Port.getText().trim());
            String email = txtAccountEmail.getText().trim();
            String password = new String(txtAccountPassword.getPassword()).trim();

            try {
                if (!email.isEmpty() && !password.isEmpty()) {
                    Pop3Client client = new Pop3Client(server, port);
                    client.connect(email, password);
                    client.dele(mail.getId());
                    client.close(); // Trong POP3, QUIT mới thực sự xác nhận xóa trên server
                } else {
                    // Mô phỏng nếu chưa có tài khoản thật
                    ProtocolLogger.getInstance().log("POP3", "C", "DELE " + mail.getId());
                    ProtocolLogger.getInstance().log("POP3", "S", "+OK message " + mail.getId() + " marked for deletion");
                    ProtocolLogger.getInstance().log("POP3", "C", "QUIT");
                    ProtocolLogger.getInstance().log("POP3", "S", "+OK POP3 server signing off (1 message deleted)");
                }

                // Xóa khỏi MySQL Database
                try {
                    DatabaseManager.deleteInboxEmail(mail.getSubject(), mail.getDate());
                } catch (Exception ignored) {}

                SwingUtilities.invokeLater(() -> {
                    currentEmails.remove(mail);
                    renderEmailList();
                    pop3StatusStats.setText("STAT: " + currentEmails.size() + " thư");

                    if (parentDialog != null) {
                        parentDialog.dispose();
                    }
                    JOptionPane.showMessageDialog(this,
                            "Đã xóa thư thành công (Lệnh POP3 DELE)!\nXem chi tiết tại tab 'Nhật ký'",
                            "Thành công", JOptionPane.INFORMATION_MESSAGE);
                });

            } catch (Exception ex) {
                ProtocolLogger.getInstance().log("POP3", "S", "LỖI DELE: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Lỗi xóa thư: " + ex.getMessage(),
                            "Lỗi POP3", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    // =========================================================================
    // 9. CÁC HÀM TIỆN ÍCH TẠO COMPONENT
    // =========================================================================
    private String getDbStatusText() {
        if (DatabaseManager.isConnected()) {
            return "Đã kết nối (MySQL)";
        } else {
            String err = DatabaseManager.getLastError();
            if (err != null && err.length() > 40) err = err.substring(0, 40) + "...";
            return "Chưa kết nối" + (err != null && !err.isEmpty() ? ": " + err : "");
        }
    }
    private JTextField createInputField(String text) {
        JTextField field = new JTextField(text);
        field.setBackground(BG_INPUT);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(GREEN_PRIMARY);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));
        return field;
    }

    private JTextField createSmallField(String text) {
        JTextField field = new JTextField(text, 14);
        field.setBackground(BG_INPUT);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(GREEN_PRIMARY);
        field.setFont(new Font("Consolas", Font.PLAIN, 12));
        field.setHorizontalAlignment(JTextField.RIGHT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(4, 8, 4, 8)
        ));
        return field;
    }

    private JButton createPrimaryButton(String text, boolean filled) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(0, 46));

        if (filled) {
            btn.setBackground(GREEN_PRIMARY);
            btn.setForeground(BG_DARKEST);
            btn.setBorder(BorderFactory.createLineBorder(GREEN_PRIMARY, 1));
        } else {
            btn.setBackground(BG_DARKEST);
            btn.setForeground(GREEN_PRIMARY);
            btn.setBorder(BorderFactory.createLineBorder(GREEN_PRIMARY, 2));
        }

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (filled) {
                    btn.setBackground(GREEN_SECONDARY);
                } else {
                    btn.setBackground(new Color(0, 255, 136, 25));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (filled) {
                    btn.setBackground(GREEN_PRIMARY);
                } else {
                    btn.setBackground(BG_DARKEST);
                }
            }
        });

        return btn;
    }

    private JButton createRoundIconButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btn.setBackground(BG_CARD);
        btn.setForeground(TEXT_MUTED);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        btn.setPreferredSize(new Dimension(36, 36));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
