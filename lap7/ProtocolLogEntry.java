import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Biểu diễn một dòng log giao thức SMTP hoặc POP3.
 */
public class ProtocolLogEntry {
    private String timestamp;
    private String type; // "SMTP" hoặc "POP3"
    private String direction; // "C" (Client gửi) hoặc "S" (Server trả lời)
    private String message;

    public ProtocolLogEntry(String type, String direction, String message) {
        this.timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        this.type = type.toUpperCase();
        this.direction = direction.toUpperCase();
        this.message = message != null ? message.trim() : "";
    }

    public String getTimestamp() { return timestamp; }
    public String getType() { return type; }
    public String getDirection() { return direction; }
    public String getMessage() { return message; }

    public String formatLog() {
        return timestamp + " " + direction + ": " + message;
    }
}
