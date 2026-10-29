import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * SMTP Client thuần socket hỗ trợ STARTTLS và SSL/TLS
 * Tuân thủ chuẩn RFC 5321 (Simple Mail Transfer Protocol).
 */
public class SmtpClient {
    private final String host;
    private final int port;
    private final ProtocolLogger logger = ProtocolLogger.getInstance();

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    public SmtpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    private void sendCommand(String cmd) throws IOException {
        logger.log("SMTP", "C", cmd);
        writer.write(cmd + "\r\n");
        writer.flush();
    }

    private String readResponse() throws IOException {
        StringBuilder fullResponse = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            logger.log("SMTP", "S", line);
            fullResponse.append(line).append("\n");
            // Trong SMTP, dòng đa phần có dạng "250-..." và dòng cuối cùng là "250 ..."
            if (line.length() >= 4 && line.charAt(3) == ' ') {
                break;
            }
            if (line.length() < 4) {
                break;
            }
        }
        return fullResponse.toString().trim();
    }

    /**
     * Mở kết nối và thực hiện bắt tay SMTP (EHLO + STARTTLS nếu port 587)
     */
    public void connect(String username, String password) throws Exception {
        logger.log("SMTP", "C", "Đang kết nối tới " + host + ":" + port + "...");

        if (port == 465) {
            // SSL trực tiếp
            SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            socket = sslFactory.createSocket(host, port);
            setupStreams();
            readResponse(); // Banner 220
            sendCommand("EHLO localhost");
            readResponse();
        } else {
            // Socket thường (port 587 STARTTLS hoặc port 25)
            socket = new Socket(host, port);
            socket.setSoTimeout(15000);
            setupStreams();

            readResponse(); // 220 banner

            sendCommand("EHLO localhost");
            String ehloResp = readResponse();

            // Nếu hỗ trợ STARTTLS hoặc port 587
            if (port == 587 || ehloResp.contains("STARTTLS")) {
                sendCommand("STARTTLS");
                String tlsResp = readResponse();
                if (!tlsResp.startsWith("220")) {
                    throw new IOException("Server không chấp nhận STARTTLS: " + tlsResp);
                }

                // Nâng cấp socket lên SSL
                SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(socket, host, port, true);
                sslSocket.startHandshake();
                this.socket = sslSocket;
                setupStreams();

                // Gửi EHLO lần nữa qua TLS
                sendCommand("EHLO localhost");
                readResponse();
            }
        }

        // Đăng nhập AUTH LOGIN nếu có thông tin tài khoản
        if (username != null && !username.trim().isEmpty() && password != null && !password.trim().isEmpty()) {
            sendCommand("AUTH LOGIN");
            String authResp = readResponse();
            if (!authResp.startsWith("334")) {
                throw new IOException("Server từ chối lệnh AUTH LOGIN: " + authResp);
            }

            // Gửi base64 username
            String userB64 = Base64.getEncoder().encodeToString(username.trim().getBytes(StandardCharsets.UTF_8));
            sendCommand(userB64);
            String userResp = readResponse();
            if (!userResp.startsWith("334")) {
                throw new IOException("Xác thực tên đăng nhập thất bại: " + userResp);
            }

            // Gửi base64 password
            String passB64 = Base64.getEncoder().encodeToString(password.trim().getBytes(StandardCharsets.UTF_8));
            // Log che mật khẩu
            logger.log("SMTP", "C", "[Mật khẩu mã hóa Base64]");
            writer.write(passB64 + "\r\n");
            writer.flush();

            String passResp = readResponse();
            if (!passResp.startsWith("235")) {
                throw new IOException("Đăng nhập thất bại: " + passResp + "\n(Gợi ý: Dùng Mật khẩu ứng dụng - App Password nếu dùng Gmail)");
            }
        }
    }

    private void setupStreams() throws IOException {
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Gửi email theo chuẩn SMTP.
     */
    public void sendEmail(String from, String to, String subject, String body) throws Exception {
        try {
            // MAIL FROM
            sendCommand("MAIL FROM:<" + from + ">");
            String mailResp = readResponse();
            if (!mailResp.startsWith("250")) {
                throw new IOException("Lỗi MAIL FROM: " + mailResp);
            }

            // RCPT TO (hỗ trợ nhiều người nhận nếu phân cách dấu phẩy)
            String[] recipients = to.split("[,;]");
            for (String r : recipients) {
                String cleanR = r.trim();
                if (!cleanR.isEmpty()) {
                    sendCommand("RCPT TO:<" + cleanR + ">");
                    String rcptResp = readResponse();
                    if (!rcptResp.startsWith("250")) {
                        throw new IOException("Lỗi RCPT TO (" + cleanR + "): " + rcptResp);
                    }
                }
            }

            // DATA
            sendCommand("DATA");
            String dataResp = readResponse();
            if (!dataResp.startsWith("354")) {
                throw new IOException("Lỗi DATA: " + dataResp);
            }

            // Gửi Headers
            SimpleDateFormat rfcFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
            String dateStr = rfcFormat.format(new Date());

            logger.log("SMTP", "C", "Subject: " + subject);
            logger.log("SMTP", "C", "From: " + from);
            logger.log("SMTP", "C", "To: " + to);

            writer.write("From: <" + from + ">\r\n");
            writer.write("To: " + to + "\r\n");
            writer.write("Subject: =?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=\r\n");
            writer.write("Date: " + dateStr + "\r\n");
            writer.write("MIME-Version: 1.0\r\n");
            writer.write("Content-Type: text/plain; charset=UTF-8\r\n");
            writer.write("Content-Transfer-Encoding: 8bit\r\n");
            writer.write("\r\n"); // Dòng trống ngăn cách Header và Body

            // Gửi Body
            String[] bodyLines = body.split("\n");
            for (String bLine : bodyLines) {
                String lineToSend = bLine.replace("\r", "");
                // Xử lý dot-stuffing (nếu dòng bắt đầu bằng dấu chấm, thêm 1 dấu chấm)
                if (lineToSend.startsWith(".")) {
                    lineToSend = "." + lineToSend;
                }
                writer.write(lineToSend + "\r\n");
                logger.log("SMTP", "C", lineToSend);
            }

            // Kết thúc data bằng CRLF.CRLF
            logger.log("SMTP", "C", ".");
            writer.write(".\r\n");
            writer.flush();

            String finishResp = readResponse();
            if (!finishResp.startsWith("250")) {
                throw new IOException("Gửi thư thất bại: " + finishResp);
            }

        } finally {
            close();
        }
    }

    /**
     * Gửi lệnh NOOP kiểm tra kết nối.
     */
    public String noop() throws Exception {
        sendCommand("NOOP");
        return readResponse();
    }

    /**
     * Gửi QUIT và đóng socket.
     */
    public void close() {
        try {
            if (writer != null) {
                sendCommand("QUIT");
                readResponse();
            }
        } catch (Exception ignored) {
        } finally {
            try { if (reader != null) reader.close(); } catch (Exception ignored) {}
            try { if (writer != null) writer.close(); } catch (Exception ignored) {}
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        }
    }
}
