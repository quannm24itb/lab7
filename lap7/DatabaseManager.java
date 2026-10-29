import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Quản lý kết nối và thao tác Cơ sở dữ liệu MySQL cho Ứng dụng Mail Client.
 * Tương thích với MySQL 8.x / 9.x và XAMPP.
 */
public class DatabaseManager {

    private static final String DB_HOST = "localhost";
    private static final int DB_PORT = 3306;
    private static final String DB_NAME = "mail_client_db";
    private static final String DB_USER = "root";
    private static final String DB_PASS = ""; // Mặc định không mật khẩu

    private static final String JDBC_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME
            + "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";

    private static boolean isConnected = false;
    private static String lastError = "";

    static {
        tryConnect();
    }

    /**
     * Thử kết nối MySQL và khởi tạo bảng.
     * Có thể gọi lại để thử kết nối lại.
     */
    public static boolean tryConnect() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            // Thử kết nối thực sự bằng cách lấy connection
            try (Connection testConn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS)) {
                System.out.println("[DatabaseManager] Kết nối MySQL thành công! URL: " + JDBC_URL);
            }
            initTables();
            isConnected = true;
            lastError = "";
            System.out.println("[DatabaseManager] Đã tạo/kiểm tra bảng xong. isConnected = true");
            return true;
        } catch (Exception e) {
            lastError = e.getMessage();
            isConnected = false;
            System.err.println("[DatabaseManager] LỖI kết nối MySQL: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isConnected() {
        return isConnected;
    }

    public static String getLastError() {
        return lastError;
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
    }

    /**
     * Tự động khởi tạo các bảng nếu chưa có
     */
    public static void initTables() {
        String sqlAccounts = "CREATE TABLE IF NOT EXISTS `accounts` ("
                + "`id` INT AUTO_INCREMENT PRIMARY KEY, "
                + "`email` VARCHAR(255) NOT NULL UNIQUE, "
                + "`password` VARCHAR(255), "
                + "`pop3_server` VARCHAR(255) DEFAULT 'pop.gmail.com', "
                + "`pop3_port` INT DEFAULT 995, "
                + "`smtp_server` VARCHAR(255) DEFAULT 'smtp.gmail.com', "
                + "`smtp_port` INT DEFAULT 587, "
                + "`updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String sqlInbox = "CREATE TABLE IF NOT EXISTS `inbox_emails` ("
                + "`id` INT AUTO_INCREMENT PRIMARY KEY, "
                + "`msg_id` INT DEFAULT 0, "
                + "`from_name` VARCHAR(255), "
                + "`from_email` VARCHAR(255), "
                + "`to_email` VARCHAR(255), "
                + "`subject` VARCHAR(500), "
                + "`date_str` VARCHAR(100), "
                + "`body` LONGTEXT, "
                + "`size_bytes` INT DEFAULT 0, "
                + "`received_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String sqlSent = "CREATE TABLE IF NOT EXISTS `sent_emails` ("
                + "`id` INT AUTO_INCREMENT PRIMARY KEY, "
                + "`from_email` VARCHAR(255), "
                + "`to_email` VARCHAR(255), "
                + "`subject` VARCHAR(500), "
                + "`body` LONGTEXT, "
                + "`status` VARCHAR(50) DEFAULT 'SUCCESS', "
                + "`sent_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String sqlLogs = "CREATE TABLE IF NOT EXISTS `protocol_logs` ("
                + "`id` INT AUTO_INCREMENT PRIMARY KEY, "
                + "`log_type` VARCHAR(10), "
                + "`direction` VARCHAR(5), "
                + "`message` TEXT, "
                + "`logged_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sqlAccounts);
            stmt.execute(sqlInbox);
            stmt.execute(sqlSent);
            stmt.execute(sqlLogs);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // =========================================================================
    // 1. TÀI KHOẢN (ACCOUNTS)
    // =========================================================================

    public static void saveAccount(String email, String password, String popServer, int popPort, String smtpServer, int smtpPort) {
        if (!isConnected || email == null || email.trim().isEmpty()) return;
        String sql = "INSERT INTO `accounts` (`email`, `password`, `pop3_server`, `pop3_port`, `smtp_server`, `smtp_port`) "
                + "VALUES (?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE `password`=?, `pop3_server`=?, `pop3_port`=?, `smtp_server`=?, `smtp_port`=?";

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, password);
            ps.setString(3, popServer);
            ps.setInt(4, popPort);
            ps.setString(5, smtpServer);
            ps.setInt(6, smtpPort);

            ps.setString(7, password);
            ps.setString(8, popServer);
            ps.setInt(9, popPort);
            ps.setString(10, smtpServer);
            ps.setInt(11, smtpPort);

            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("[DB] Lỗi lưu tài khoản: " + e.getMessage());
        }
    }

    public static AccountData getLatestAccount() {
        if (!isConnected) return null;
        String sql = "SELECT * FROM `accounts` ORDER BY `updated_at` DESC LIMIT 1";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return new AccountData(
                        rs.getString("email"),
                        rs.getString("password"),
                        rs.getString("pop3_server"),
                        rs.getInt("pop3_port"),
                        rs.getString("smtp_server"),
                        rs.getInt("smtp_port")
                );
            }
        } catch (Exception e) {
            System.err.println("[DB] Lỗi đọc tài khoản: " + e.getMessage());
        }
        return null;
    }

    public static class AccountData {
        public String email;
        public String password;
        public String popServer;
        public int popPort;
        public String smtpServer;
        public int smtpPort;

        public AccountData(String email, String password, String popServer, int popPort, String smtpServer, int smtpPort) {
            this.email = email;
            this.password = password;
            this.popServer = popServer;
            this.popPort = popPort;
            this.smtpServer = smtpServer;
            this.smtpPort = smtpPort;
        }
    }

    // =========================================================================
    // 2. HỘP THƯ ĐẾN (INBOX EMAILS)
    // =========================================================================

    public static void saveInboxEmails(List<EmailModel> emails) {
        if (!isConnected || emails == null || emails.isEmpty()) return;
        String checkSql = "SELECT COUNT(*) FROM `inbox_emails` WHERE `subject`=? AND `date_str`=? AND `from_email`=?";
        String insertSql = "INSERT INTO `inbox_emails` (`msg_id`, `from_name`, `from_email`, `to_email`, `subject`, `date_str`, `body`, `size_bytes`) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement checkPs = conn.prepareStatement(checkSql);
             PreparedStatement insertPs = conn.prepareStatement(insertSql)) {

            for (EmailModel mail : emails) {
                // Kiểm tra xem thư đã có trong DB chưa để tránh lưu trùng
                checkPs.setString(1, mail.getSubject());
                checkPs.setString(2, mail.getDate());
                checkPs.setString(3, mail.getFromEmail());
                ResultSet rs = checkPs.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    continue; // Đã có rồi, bỏ qua
                }

                insertPs.setInt(1, mail.getId());
                insertPs.setString(2, mail.getFromName());
                insertPs.setString(3, mail.getFromEmail());
                insertPs.setString(4, mail.getTo());
                insertPs.setString(5, mail.getSubject());
                insertPs.setString(6, mail.getDate());
                insertPs.setString(7, mail.getBody());
                insertPs.setInt(8, mail.getSize());
                insertPs.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("[DB] Lỗi lưu email vào inbox: " + e.getMessage());
        }
    }

    public static List<EmailModel> loadInboxEmails() {
        List<EmailModel> list = new ArrayList<>();
        if (!isConnected) return list;

        String sql = "SELECT * FROM `inbox_emails` ORDER BY `id` DESC LIMIT 50";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new EmailModel(
                        rs.getInt("msg_id"),
                        rs.getString("from_name"),
                        rs.getString("from_email"),
                        rs.getString("to_email"),
                        rs.getString("subject"),
                        rs.getString("date_str"),
                        rs.getString("body"),
                        rs.getInt("size_bytes")
                ));
            }
        } catch (Exception e) {
            System.err.println("[DB] Lỗi đọc danh sách email: " + e.getMessage());
        }
        return list;
    }

    public static void deleteInboxEmail(String subject, String dateStr) {
        if (!isConnected) return;
        String sql = "DELETE FROM `inbox_emails` WHERE `subject`=? AND `date_str`=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, subject);
            ps.setString(2, dateStr);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("[DB] Lỗi xóa email: " + e.getMessage());
        }
    }

    // =========================================================================
    // 3. THƯ ĐÃ GỬI (SENT EMAILS)
    // =========================================================================

    public static void saveSentEmail(String from, String to, String subject, String body) {
        if (!isConnected) {
            System.err.println("[DB] saveSentEmail: CHƯA KẾT NỐI MySQL!");
            return;
        }
        String sql = "INSERT INTO `sent_emails` (`from_email`, `to_email`, `subject`, `body`) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from);
            ps.setString(2, to);
            ps.setString(3, subject);
            ps.setString(4, body);
            int rows = ps.executeUpdate();
            System.out.println("[DB] Đã lưu thư đã gửi: " + rows + " dòng - Chủ đề: " + subject);
        } catch (Exception e) {
            System.err.println("[DB] Lỗi lưu thư đã gửi: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Lấy danh sách thư đã gửi từ MySQL
     */
    public static List<EmailModel> loadSentEmails() {
        List<EmailModel> list = new ArrayList<>();
        if (!isConnected) return list;
        String sql = "SELECT * FROM `sent_emails` ORDER BY `id` DESC LIMIT 50";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            int idx = 1;
            while (rs.next()) {
                String sentAt = rs.getString("sent_at");
                if (sentAt != null && sentAt.length() > 16) sentAt = sentAt.substring(11, 16); // HH:mm
                list.add(new EmailModel(
                        idx++,
                        "Tôi",
                        rs.getString("from_email"),
                        rs.getString("to_email"),
                        rs.getString("subject"),
                        sentAt != null ? sentAt : "",
                        rs.getString("body"),
                        0
                ));
            }
        } catch (Exception e) {
            System.err.println("[DB] Lỗi đọc thư đã gửi: " + e.getMessage());
        }
        return list;
    }

    // =========================================================================
    // 4. NHẬT KÝ GIAO THỨC (PROTOCOL LOGS)
    // =========================================================================

    public static void saveLog(String type, String direction, String message) {
        if (!isConnected) return;
        String sql = "INSERT INTO `protocol_logs` (`log_type`, `direction`, `message`) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setString(2, direction);
            ps.setString(3, message);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }
}
