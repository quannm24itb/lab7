import java.util.Date;

/**
 * Model biểu diễn một email trong hộp thư đến.
 */
public class EmailModel {
    private int id;
    private String fromName;
    private String fromEmail;
    private String to;
    private String subject;
    private String date;
    private String body;
    private String preview;
    private int size;

    public EmailModel(int id, String fromName, String fromEmail, String to, String subject, String date, String body, int size) {
        this.id = id;
        this.fromName = fromName;
        this.fromEmail = fromEmail;
        this.to = to;
        this.subject = (subject == null || subject.trim().isEmpty()) ? "(Không có tiêu đề)" : subject;
        this.date = date;
        this.body = body;
        this.size = size;
        if (body != null) {
            String clean = body.replaceAll("\\s+", " ").trim();
            this.preview = clean.length() > 60 ? clean.substring(0, 60) + "..." : clean;
        } else {
            this.preview = "";
        }
    }

    public int getId() { return id; }
    public String getFromName() { return fromName; }
    public String getFromEmail() { return fromEmail; }
    public String getTo() { return to; }
    public String getSubject() { return subject; }
    public String getDate() { return date; }
    public String getBody() { return body; }
    public String getPreview() { return preview; }
    public int getSize() { return size; }

    @Override
    public String toString() {
        return fromName + " - " + subject;
    }
}
