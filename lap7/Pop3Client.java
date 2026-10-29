import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLSocketFactory;

/**
 * POP3 Client thuần socket hỗ trợ SSL/TLS (Port 995) và Plain (Port 110).
 * Tuân thủ chuẩn RFC 1939 (Post Office Protocol - Version 3).
 */
public class Pop3Client {
    private final String host;
    private final int port;
    private final ProtocolLogger logger = ProtocolLogger.getInstance();

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    public static class StatResult {
        public int count;
        public long size;

        public StatResult(int count, long size) {
            this.count = count;
            this.size = size;
        }
    }

    public Pop3Client(String host, int port) {
        this.host = host;
        this.port = port;
    }

    private void sendCommand(String cmd) throws IOException {
        logger.log("POP3", "C", cmd);
        writer.write(cmd + "\r\n");
        writer.flush();
    }

    private String readSingleLine() throws IOException {
        String line = reader.readLine();
        if (line != null) {
            logger.log("POP3", "S", line);
        }
        return line;
    }

    private List<String> readMultiLine() throws IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (".".equals(line)) {
                break;
            }
            // Byte-stuffed termination check: dòng bắt đầu bằng .. được bỏ bớt 1 dấu .
            if (line.startsWith("..")) {
                line = line.substring(1);
            }
            lines.add(line);
        }
        return lines;
    }

    /**
     * Kết nối và xác thực với POP3 server.
     */
    public void connect(String username, String password) throws Exception {
        logger.log("POP3", "C", "Đang kết nối tới " + host + ":" + port + (port == 995 ? " (SSL)..." : "..."));

        if (port == 995) {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            socket = factory.createSocket(host, port);
        } else {
            socket = new Socket(host, port);
        }
        socket.setSoTimeout(15000);

        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

        // Nhận welcome banner
        String welcome = readSingleLine();
        if (welcome == null || !welcome.startsWith("+OK")) {
            throw new IOException("Server POP3 không trả về +OK: " + welcome);
        }

        // Gửi USER
        sendCommand("USER " + username);
        String userResp = readSingleLine();
        if (userResp == null || !userResp.startsWith("+OK")) {
            throw new IOException("Lỗi lệnh USER: " + userResp);
        }

        // Gửi PASS
        logger.log("POP3", "C", "PASS ********");
        writer.write("PASS " + password + "\r\n");
        writer.flush();

        String passResp = readSingleLine();
        if (passResp == null || !passResp.startsWith("+OK")) {
            throw new IOException("Đăng nhập POP3 thất bại: " + passResp + "\n(Gợi ý: Kiểm tra đã bật POP trong cài đặt Gmail và tạo App Password)");
        }
    }

    /**
     * Lệnh STAT: Lấy số lượng email và tổng dung lượng.
     */
    public StatResult stat() throws Exception {
        sendCommand("STAT");
        String resp = readSingleLine();
        if (resp == null || !resp.startsWith("+OK")) {
            throw new IOException("Lỗi STAT: " + resp);
        }
        String[] parts = resp.split("\\s+");
        int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        long size = parts.length > 2 ? Long.parseLong(parts[2]) : 0;
        return new StatResult(count, size);
    }

    /**
     * Lệnh LIST: Lấy danh sách kích thước các thư.
     */
    public List<String> list() throws Exception {
        sendCommand("LIST");
        String resp = readSingleLine();
        if (resp == null || !resp.startsWith("+OK")) {
            throw new IOException("Lỗi LIST: " + resp);
        }
        List<String> listItems = readMultiLine();
        // Log một số dòng tiêu biểu để nhật ký không quá dài
        int logCount = Math.min(listItems.size(), 5);
        for (int i = 0; i < logCount; i++) {
            logger.log("POP3", "S", listItems.get(i));
        }
        if (listItems.size() > 5) {
            logger.log("POP3", "S", "... và " + (listItems.size() - 5) + " thư khác");
        }
        return listItems;
    }

    /**
     * Lệnh RETR: Tải nội dung đầy đủ của email có thứ tự msgId.
     */
    public EmailModel retr(int msgId) throws Exception {
        sendCommand("RETR " + msgId);
        String resp = readSingleLine();
        if (resp == null || !resp.startsWith("+OK")) {
            throw new IOException("Lỗi RETR " + msgId + ": " + resp);
        }
        List<String> rawLines = readMultiLine();
        return parseEmail(msgId, rawLines);
    }

    /**
     * Lệnh TOP: Lấy tiêu đề và một số dòng nội dung đầu.
     */
    public EmailModel top(int msgId, int linesCount) throws Exception {
        sendCommand("TOP " + msgId + " " + linesCount);
        String resp = readSingleLine();
        if (resp == null || !resp.startsWith("+OK")) {
            throw new IOException("Lỗi TOP " + msgId + ": " + resp);
        }
        List<String> rawLines = readMultiLine();
        return parseEmail(msgId, rawLines);
    }

    /**
     * Lệnh DELE: Đánh dấu xóa email có thứ tự msgId trên server.
     */
    public String dele(int msgId) throws Exception {
        sendCommand("DELE " + msgId);
        String resp = readSingleLine();
        if (resp == null || !resp.startsWith("+OK")) {
            throw new IOException("Lỗi DELE " + msgId + ": " + resp);
        }
        return resp;
    }

    /**
     * Lệnh NOOP.
     */
    public String noop() throws Exception {
        sendCommand("NOOP");
        return readSingleLine();
    }

    /**
     * Lệnh QUIT.
     */
    public void close() {
        try {
            if (writer != null) {
                sendCommand("QUIT");
                readSingleLine();
            }
        } catch (Exception ignored) {
        } finally {
            try { if (reader != null) reader.close(); } catch (Exception ignored) {}
            try { if (writer != null) writer.close(); } catch (Exception ignored) {}
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Phân tích nội dung thô của email từ POP3 RETR/TOP.
     */
    public static EmailModel parseEmail(int msgId, List<String> lines) {
        String from = "Không rõ";
        String to = "";
        String subject = "(Không có tiêu đề)";
        String date = "";
        List<String> bodyLines = new ArrayList<>();

        boolean inHeader = true;
        for (String line : lines) {
            if (inHeader) {
                if (line.isEmpty()) {
                    inHeader = false;
                    continue;
                }
                String lower = line.toLowerCase();
                if (lower.startsWith("from:")) {
                    from = decodeMime(line.substring(5).trim());
                } else if (lower.startsWith("to:")) {
                    to = decodeMime(line.substring(3).trim());
                } else if (lower.startsWith("subject:")) {
                    subject = decodeMime(line.substring(8).trim());
                } else if (lower.startsWith("date:")) {
                    date = line.substring(5).trim();
                }
            } else {
                bodyLines.add(line);
            }
        }

        // Tách From Name và From Email
        String fromName = from;
        String fromEmail = from;
        if (from.contains("<") && from.contains(">")) {
            int start = from.indexOf("<");
            int end = from.indexOf(">");
            fromEmail = from.substring(start + 1, end).trim();
            fromName = from.substring(0, start).trim().replace("\"", "");
            if (fromName.isEmpty()) fromName = fromEmail;
        }

        // Làm sạch body: Loại bỏ hoàn toàn các boundary ranh giới MIME (--000000...) và sub-headers
        String cleanBody = extractCleanBody(bodyLines);

        return new EmailModel(msgId, fromName, fromEmail, to, subject, date, cleanBody, lines.size() * 30);
    }

    /**
     * Bóc tách nội dung văn bản thuần (text/plain) sạch sẽ từ email MIME multipart,
     * loại bỏ hoàn toàn các dòng ranh giới (--00000000...) và các header phụ.
     */
    private static String extractCleanBody(List<String> bodyLines) {
        if (bodyLines == null || bodyLines.isEmpty()) {
            return "";
        }

        // Kiểm tra xem có cấu trúc Multipart không (có dòng bắt đầu bằng --)
        boolean isMultipart = false;
        for (String line : bodyLines) {
            String t = line.trim();
            if (t.startsWith("--") && t.length() > 6) {
                isMultipart = true;
                break;
            }
        }

        if (!isMultipart) {
            // Email đơn giản một phần (Single part)
            StringBuilder sb = new StringBuilder();
            for (String line : bodyLines) {
                sb.append(line).append("\n");
            }
            return decodeQuotedPrintable(sb.toString().trim());
        }

        // Với email Multipart (chứa nhiều phần như text/plain, text/html)
        StringBuilder textPlain = new StringBuilder();
        StringBuilder textHtml = new StringBuilder();
        boolean inTextPlain = false;
        boolean inTextHtml = false;
        boolean inPartHeader = false;

        for (String line : bodyLines) {
            String trimmed = line.trim();

            // Nếu gặp dòng boundary phân cách (--00000000000...)
            if (trimmed.startsWith("--") && trimmed.length() > 6) {
                inTextPlain = false;
                inTextHtml = false;
                inPartHeader = true;
                continue;
            }

            // Đang duyệt phần header con của part
            if (inPartHeader) {
                if (trimmed.isEmpty()) {
                    // Dòng trống kết thúc sub-header, bắt đầu nội dung
                    inPartHeader = false;
                    continue;
                }
                String lower = trimmed.toLowerCase();
                if (lower.startsWith("content-type:")) {
                    if (lower.contains("text/plain")) {
                        inTextPlain = true;
                        inTextHtml = false;
                    } else if (lower.contains("text/html")) {
                        inTextHtml = true;
                        inTextPlain = false;
                    }
                }
                continue;
            }

            // Ghi nhận nội dung thực sự
            if (inTextPlain) {
                textPlain.append(line).append("\n");
            } else if (inTextHtml && textPlain.length() == 0) {
                textHtml.append(line).append("\n");
            }
        }

        String result = textPlain.toString().trim();
        // Nếu không tìm thấy text/plain, thử trích xuất văn bản từ text/html
        if (result.isEmpty() && textHtml.length() > 0) {
            result = textHtml.toString()
                    .replaceAll("<style[^>]*>[\\s\\S]*?</style>", "")
                    .replaceAll("<script[^>]*>[\\s\\S]*?</script>", "")
                    .replaceAll("<[^>]*>", " ")
                    .replaceAll("&nbsp;", " ")
                    .replaceAll("&lt;", "<")
                    .replaceAll("&gt;", ">")
                    .replaceAll("&amp;", "&")
                    .replaceAll("\\s+", " ")
                    .trim();
        }

        // Fallback: nếu vẫn rỗng thì lọc bỏ các dòng rác boundary và metadata
        if (result.isEmpty()) {
            StringBuilder fallback = new StringBuilder();
            for (String line : bodyLines) {
                String t = line.trim();
                if (!t.startsWith("--") 
                        && !t.toLowerCase().startsWith("content-type:")
                        && !t.toLowerCase().startsWith("content-transfer-encoding:")
                        && !t.toLowerCase().startsWith("charset=")
                        && !t.toLowerCase().startsWith("content-disposition:")) {
                    fallback.append(line).append("\n");
                }
            }
            result = fallback.toString().trim();
        }

        return decodeQuotedPrintable(result);
    }

    /**
     * Giải mã chuẩn Quoted-Printable (=E1=BA=A1 hoặc soft-break =)
     */
    public static String decodeQuotedPrintable(String input) {
        if (input == null || !input.contains("=")) {
            return input != null ? input : "";
        }
        try {
            // Xóa soft line break (= ở cuối dòng)
            String text = input.replaceAll("=\r?\n", "");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
            for (int i = 0; i < bytes.length; i++) {
                byte b = bytes[i];
                if (b == '=' && i + 2 < bytes.length) {
                    int h1 = Character.digit((char) bytes[i + 1], 16);
                    int h2 = Character.digit((char) bytes[i + 2], 16);
                    if (h1 != -1 && h2 != -1) {
                        baos.write((h1 << 4) + h2);
                        i += 2;
                        continue;
                    }
                }
                baos.write(b);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return input;
        }
    }

    /**
     * Giải mã chuỗi MIME encoded (RFC 2047: =?UTF-8?B?...?= hoặc =?UTF-8?Q?...?=)
     */
    private static String decodeMime(String input) {
        if (input == null) return "";
        try {
            Pattern pattern = Pattern.compile("=\\?([^?]+)\\?([BQbq])\\?([^?]+)\\?=");
            Matcher matcher = pattern.matcher(input);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String charset = matcher.group(1);
                String encoding = matcher.group(2).toUpperCase();
                String data = matcher.group(3);
                String decoded = data;
                if ("B".equals(encoding)) {
                    byte[] bytes = Base64.getDecoder().decode(data);
                    decoded = new String(bytes, charset);
                } else if ("Q".equals(encoding)) {
                    decoded = decodeQuotedPrintable(data.replace("_", " "));
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(decoded));
            }
            matcher.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }
}
