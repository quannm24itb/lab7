# Ứng Dụng SMTP & POP3 Client (Java Swing)

Ứng dụng mô phỏng Email Client trên giao diện điện thoại Dark Theme theo đúng thiết kế yêu cầu của môn Lập trình mạng.

---

## 🛠️ Cách Chạy Trong Visual Studio Code

### Cách 1: Chạy trực tiếp từ nút Run / Phím F5 (Khuyên dùng)
1. Mở thư mục `lap7` bằng VS Code:
   `File` -> `Open Folder...` -> chọn thư mục `lap7`.
2. Mở file [`MailAppGUI.java`](file:///c:/Users/qminh/Music/mon/ltm/lap7/MailAppGUI.java).
3. Nhấn nút **Run** (biểu tượng tam giác) ở góc trên bên phải của VS Code hoặc nhấn phím **F5**.
4. Ứng dụng Java Swing sẽ xuất hiện với đầy đủ 4 màn hình.

---

### Cách 2: Chạy bằng Terminal trong VS Code
Mở Terminal trong VS Code (`Ctrl + ~`) và gõ 2 lệnh sau:

```powershell
javac -encoding UTF-8 *.java
java -Dfile.encoding=UTF-8 MailAppGUI
```

---

### Cách 3: Nhấp đúp file run.bat
- Bạn chỉ cần nhấp đúp vào file [`run.bat`](file:///c:/Users/qminh/Music/mon/ltm/lap7/run.bat) trong thư mục dự án là ứng dụng sẽ tự động biên dịch và mở lên.

---

## 📱 4 Màn Hình Chức Năng (Theo Đúng Thiết Kế Mockup)

1. **Hộp thư đến (POP3 Client)**:
   - Kết nối tới server POP3 qua cổng **995 (SSL/TLS)**.
   - Hiển thị danh sách email với chấm xanh, tên người gửi, tiêu đề, tóm tắt nội dung và thời gian.
   - Nhấn vào bất kỳ email nào để mở popup xem chi tiết đầy đủ nội dung.
   - Nút bấm to: `↓ RETR · Tải thư mới` để thực hiện lấy thư từ máy chủ.

2. **Soạn thư (SMTP Client)**:
   - Kết nối tới server SMTP qua cổng **587 (STARTTLS)**.
   - Các trường nhập liệu: *Từ*, *Đến*, *Chủ đề*, và vùng soạn nội dung.
   - Nút bấm to: `✈ DATA · Gửi thư` để thực thi chuỗi lệnh SMTP gửi email.

3. **Nhật ký giao thức (Protocol Log)**:
   - Bộ lọc tab: `Tất cả`, `SMTP`, `POP3`.
   - Màn hình log hiển thị chi tiết mọi lệnh trao đổi:
     - Dòng `S:` (Server phản hồi) hiển thị màu **Xanh lá neon**.
     - Dòng `C:` (Client gửi lệnh) hiển thị màu **Trắng**.
     - Dấu mốc thời gian `HH:mm:ss` màu xám.
   - Thanh nhập lệnh nhanh ở dưới: hỗ trợ gõ lệnh `NOOP` và nhấn nút gửi `↑` trực tiếp.

4. **Tài khoản**:
   - Cấu hình Server & Cổng cho POP3 (`pop.gmail.com:995`).
   - Cấu hình Server & Cổng cho SMTP (`smtp.gmail.com:587`).
   - Nhập Email và Mật khẩu (Dùng Mật khẩu ứng dụng - App Password nếu dùng Gmail).
   - Nút `Kiểm tra kết nối`: tự động kết nối thử nghiệm cả SMTP & POP3 và hiển thị kết quả.

---

## 🔌 Cấu Trúc Mã Nguồn Java Thuần (Không Cần Thư Viện Ngoài)

| Tên File | Chức Năng |
| :--- | :--- |
| [`MailAppGUI.java`](file:///c:/Users/qminh/Music/mon/ltm/lap7/MailAppGUI.java) | Toàn bộ giao diện Java Swing chuẩn dark theme 4 tab |
| [`SmtpClient.java`](file:///c:/Users/qminh/Music/mon/ltm/lap7/SmtpClient.java) | Socket SMTP thuần: `EHLO`, `STARTTLS`, `AUTH LOGIN`, `MAIL FROM`, `RCPT TO`, `DATA`, `QUIT`, `NOOP` |
| [`Pop3Client.java`](file:///c:/Users/qminh/Music/mon/ltm/lap7/Pop3Client.java) | SSLSocket POP3 thuần: `USER`, `PASS`, `STAT`, `LIST`, `RETR`, `TOP`, `QUIT`, `NOOP` |
| [`EmailModel.java`](file:///c:/Users/qminh/Music/mon/ltm/lap7/EmailModel.java) | Model lưu trữ thuộc tính email |
| [`ProtocolLogger.java`](file:///c:/Users/qminh/Music/mon/ltm/lap7/ProtocolLogger.java) | Quản lý nhật ký sự kiện giao thức thời gian thực |
| [`ProtocolLogEntry.java`](file:///c:/Users/qminh/Music/mon/ltm/lap7/ProtocolLogEntry.java) | Thực thể lưu từng dòng log |
