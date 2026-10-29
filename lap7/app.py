"""
SMTP/POP3 Email Client - Flask Backend
Ứng dụng email client hỗ trợ gửi thư qua SMTP và nhận thư qua POP3
với ghi nhật ký giao thức chi tiết.
"""

import smtplib
import poplib
import ssl
import email
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from email.header import decode_header
from email.utils import parseaddr, formatdate
from datetime import datetime
from flask import Flask, render_template, request, jsonify
from flask_cors import CORS
import traceback

app = Flask(__name__)
CORS(app)

# ============================================================
# Bộ nhớ lưu nhật ký giao thức (Protocol Log Storage)
# ============================================================
protocol_logs = []


def add_log(log_type, direction, message):
    """Thêm một dòng nhật ký giao thức.
    
    Args:
        log_type: 'smtp' hoặc 'pop3'
        direction: 'C' (client gửi) hoặc 'S' (server trả lời)
        message: Nội dung lệnh/phản hồi
    """
    entry = {
        'timestamp': datetime.now().strftime('%H:%M:%S'),
        'type': log_type,
        'direction': direction,
        'message': str(message).strip()
    }
    protocol_logs.append(entry)
    try:
        print(f"[{entry['timestamp']}] {entry['type'].upper()} {entry['direction']}: {entry['message']}")
    except UnicodeEncodeError:
        print(f"[{entry['timestamp']}] {entry['type'].upper()} {entry['direction']}: [unicode content]")


# ============================================================
# SMTP Client có ghi nhật ký (Logged SMTP)
# ============================================================
class LoggedSMTP:
    """Wrapper cho smtplib.SMTP với ghi nhật ký đầy đủ."""
    
    def __init__(self, host, port, timeout=30):
        self.host = host
        self.port = port
        add_log('smtp', 'C', f'Đang kết nối tới {host}:{port}...')
        
        ctx = ssl.create_default_context()
        self.smtp = smtplib.SMTP(host, port, timeout=timeout)
        
        # Lấy banner chào mừng
        banner = f'{self.smtp.ehlo_resp}'
        if self.smtp.sock:
            add_log('smtp', 'S', f'220 {host} ESMTP ready')
    
    def ehlo(self):
        add_log('smtp', 'C', 'EHLO localhost')
        code, msg = self.smtp.ehlo()
        for line in msg.decode('utf-8', errors='replace').split('\n'):
            add_log('smtp', 'S', f'{code}-{line.strip()}' if '\n' in msg.decode('utf-8', errors='replace') else f'{code} {line.strip()}')
        return code, msg
    
    def starttls(self):
        add_log('smtp', 'C', 'STARTTLS')
        code, msg = self.smtp.starttls()
        add_log('smtp', 'S', f'{code} {msg.decode("utf-8", errors="replace")}')
        return code, msg
    
    def login(self, user, password):
        add_log('smtp', 'C', f'AUTH LOGIN')
        try:
            code, msg = self.smtp.login(user, password)
            add_log('smtp', 'S', f'{code} {msg.decode("utf-8", errors="replace")}')
            return code, msg
        except smtplib.SMTPAuthenticationError as e:
            add_log('smtp', 'S', f'{e.smtp_code} {e.smtp_error.decode("utf-8", errors="replace")}')
            raise
    
    def mail(self, sender):
        add_log('smtp', 'C', f'MAIL FROM:<{sender}>')
        code, msg = self.smtp.mail(sender)
        add_log('smtp', 'S', f'{code} {msg.decode("utf-8", errors="replace")}')
        return code, msg
    
    def rcpt(self, recipient):
        add_log('smtp', 'C', f'RCPT TO:<{recipient}>')
        code, msg = self.smtp.rcpt(recipient)
        add_log('smtp', 'S', f'{code} {msg.decode("utf-8", errors="replace")}')
        return code, msg
    
    def data(self, msg_data):
        add_log('smtp', 'C', 'DATA')
        add_log('smtp', 'S', '354 End data with <CR><LF>.<CR><LF>')
        code, msg = self.smtp.data(msg_data)
        add_log('smtp', 'S', f'{code} {msg.decode("utf-8", errors="replace")}')
        return code, msg
    
    def noop(self):
        add_log('smtp', 'C', 'NOOP')
        code, msg = self.smtp.noop()
        add_log('smtp', 'S', f'{code} {msg.decode("utf-8", errors="replace")}')
        return code, msg
    
    def quit(self):
        add_log('smtp', 'C', 'QUIT')
        try:
            code, msg = self.smtp.quit()
            add_log('smtp', 'S', f'{code} {msg.decode("utf-8", errors="replace")}')
            return code, msg
        except Exception:
            pass
    
    def sendmail(self, from_addr, to_addr, msg_string):
        """Gửi email với ghi nhật ký từng bước SMTP."""
        self.mail(from_addr)
        self.rcpt(to_addr)
        self.data(msg_string.encode('utf-8'))


# ============================================================
# POP3 Client có ghi nhật ký (Logged POP3)
# ============================================================
class LoggedPOP3:
    """Wrapper cho poplib.POP3_SSL với ghi nhật ký đầy đủ."""
    
    def __init__(self, host, port, timeout=30):
        self.host = host
        self.port = port
        add_log('pop3', 'C', f'Đang kết nối tới {host}:{port} (SSL)...')
        
        self.pop3 = poplib.POP3_SSL(host, port, timeout=timeout)
        welcome = self.pop3.getwelcome().decode('utf-8', errors='replace')
        add_log('pop3', 'S', welcome)
    
    def user(self, username):
        add_log('pop3', 'C', f'USER {username}')
        resp = self.pop3.user(username)
        add_log('pop3', 'S', resp.decode('utf-8', errors='replace'))
        return resp
    
    def pass_(self, password):
        add_log('pop3', 'C', 'PASS ********')
        resp = self.pop3.pass_(password)
        add_log('pop3', 'S', resp.decode('utf-8', errors='replace'))
        return resp
    
    def stat(self):
        add_log('pop3', 'C', 'STAT')
        count, size = self.pop3.stat()
        add_log('pop3', 'S', f'+OK {count} {size}')
        return count, size
    
    def list(self, which=None):
        add_log('pop3', 'C', 'LIST')
        resp, listings, octets = self.pop3.list(which)
        add_log('pop3', 'S', resp.decode('utf-8', errors='replace'))
        for item in listings[:5]:  # Chỉ log 5 dòng đầu để tránh spam
            add_log('pop3', 'S', item.decode('utf-8', errors='replace'))
        if len(listings) > 5:
            add_log('pop3', 'S', f'... và {len(listings) - 5} mục khác')
        return resp, listings, octets
    
    def retr(self, which):
        add_log('pop3', 'C', f'RETR {which}')
        resp, lines, octets = self.pop3.retr(which)
        add_log('pop3', 'S', f'{resp.decode("utf-8", errors="replace")} ({octets} bytes)')
        return resp, lines, octets
    
    def top(self, which, howmuch=0):
        add_log('pop3', 'C', f'TOP {which} {howmuch}')
        resp, lines, octets = self.pop3.top(which, howmuch)
        add_log('pop3', 'S', resp.decode('utf-8', errors='replace'))
        return resp, lines, octets
    
    def dele(self, which):
        add_log('pop3', 'C', f'DELE {which}')
        resp = self.pop3.dele(which)
        add_log('pop3', 'S', resp.decode('utf-8', errors='replace'))
        return resp
    
    def noop(self):
        add_log('pop3', 'C', 'NOOP')
        resp = self.pop3.noop()
        add_log('pop3', 'S', resp.decode('utf-8', errors='replace'))
        return resp
    
    def quit(self):
        add_log('pop3', 'C', 'QUIT')
        try:
            resp = self.pop3.quit()
            add_log('pop3', 'S', resp.decode('utf-8', errors='replace'))
            return resp
        except Exception:
            pass


# ============================================================
# Hàm tiện ích phân tích email (Email Parsing Utilities)
# ============================================================
def decode_mime_header(header_value):
    """Giải mã header email theo RFC 2047."""
    if header_value is None:
        return ''
    decoded_parts = decode_header(header_value)
    result = []
    for part, charset in decoded_parts:
        if isinstance(part, bytes):
            result.append(part.decode(charset or 'utf-8', errors='replace'))
        else:
            result.append(part)
    return ' '.join(result)


def parse_email_message(raw_lines):
    """Phân tích email thô thành dict có cấu trúc.
    
    Args:
        raw_lines: List các dòng bytes từ POP3 RETR
    
    Returns:
        Dict chứa from, to, subject, date, body
    """
    raw_data = b'\r\n'.join(raw_lines)
    msg = email.message_from_bytes(raw_data)
    
    # Phân tích header
    from_name, from_email = parseaddr(decode_mime_header(msg.get('From', '')))
    subject = decode_mime_header(msg.get('Subject', '(Không có chủ đề)'))
    date_str = msg.get('Date', '')
    to_addr = decode_mime_header(msg.get('To', ''))
    
    # Phân tích thời gian
    try:
        from email.utils import parsedate_to_datetime
        date_obj = parsedate_to_datetime(date_str)
        formatted_date = date_obj.strftime('%d/%m/%Y %H:%M')
    except Exception:
        formatted_date = date_str
    
    # Phân tích nội dung (body)
    body = ''
    if msg.is_multipart():
        for part in msg.walk():
            content_type = part.get_content_type()
            if content_type == 'text/plain':
                payload = part.get_payload(decode=True)
                charset = part.get_content_charset() or 'utf-8'
                body = payload.decode(charset, errors='replace')
                break
            elif content_type == 'text/html' and not body:
                payload = part.get_payload(decode=True)
                charset = part.get_content_charset() or 'utf-8'
                body = payload.decode(charset, errors='replace')
    else:
        payload = msg.get_payload(decode=True)
        if payload:
            charset = msg.get_content_charset() or 'utf-8'
            body = payload.decode(charset, errors='replace')
    
    return {
        'from_name': from_name or from_email.split('@')[0] if from_email else 'Không rõ',
        'from_email': from_email,
        'to': to_addr,
        'subject': subject,
        'date': formatted_date,
        'date_raw': date_str,
        'body': body,
        'preview': body[:100].replace('\n', ' ').replace('\r', '') if body else ''
    }


# ============================================================
# API Routes
# ============================================================

@app.route('/')
def index():
    """Trang chủ - Hiển thị giao diện email client."""
    return render_template('index.html')


@app.route('/api/smtp/send', methods=['POST'])
def smtp_send():
    """Gửi email qua giao thức SMTP.
    
    Body JSON:
        smtp_server, smtp_port, email, password,
        to, subject, body
    """
    try:
        data = request.json
        server = data.get('smtp_server', 'smtp.gmail.com')
        port = int(data.get('smtp_port', 587))
        user_email = data['email']
        password = data['password']
        to_addr = data['to']
        subject = data['subject']
        body_text = data['body']
        
        # Tạo email message
        msg = MIMEMultipart()
        msg['From'] = user_email
        msg['To'] = to_addr
        msg['Subject'] = subject
        msg['Date'] = formatdate(localtime=True)
        msg.attach(MIMEText(body_text, 'plain', 'utf-8'))
        
        # Kết nối và gửi qua SMTP
        smtp = LoggedSMTP(server, port)
        smtp.ehlo()
        smtp.starttls()
        smtp.ehlo()
        smtp.login(user_email, password)
        
        # Log thêm thông tin email
        add_log('smtp', 'C', f'Subject: {subject}')
        add_log('smtp', 'C', f'From: {user_email}')
        add_log('smtp', 'C', f'To: {to_addr}')
        
        smtp.sendmail(user_email, to_addr, msg.as_string())
        smtp.quit()
        
        return jsonify({
            'success': True,
            'message': f'Đã gửi email thành công tới {to_addr}!'
        })
        
    except smtplib.SMTPAuthenticationError as e:
        add_log('smtp', 'S', f'LỖI XÁC THỰC: {str(e)}')
        return jsonify({
            'success': False,
            'message': 'Lỗi xác thực! Kiểm tra email và mật khẩu ứng dụng (App Password).'
        }), 401
    except smtplib.SMTPException as e:
        add_log('smtp', 'S', f'LỖI SMTP: {str(e)}')
        return jsonify({
            'success': False,
            'message': f'Lỗi SMTP: {str(e)}'
        }), 500
    except Exception as e:
        add_log('smtp', 'S', f'LỖI: {str(e)}')
        return jsonify({
            'success': False,
            'message': f'Lỗi: {str(e)}'
        }), 500


@app.route('/api/smtp/noop', methods=['POST'])
def smtp_noop():
    """Gửi lệnh NOOP tới SMTP server (kiểm tra kết nối)."""
    try:
        data = request.json
        server = data.get('smtp_server', 'smtp.gmail.com')
        port = int(data.get('smtp_port', 587))
        user_email = data['email']
        password = data['password']
        
        smtp = LoggedSMTP(server, port)
        smtp.ehlo()
        smtp.starttls()
        smtp.ehlo()
        smtp.login(user_email, password)
        smtp.noop()
        smtp.quit()
        
        return jsonify({'success': True, 'message': 'NOOP thành công!'})
    except Exception as e:
        add_log('smtp', 'S', f'LỖI NOOP: {str(e)}')
        return jsonify({'success': False, 'message': f'Lỗi: {str(e)}'}), 500


@app.route('/api/smtp/check', methods=['POST'])
def smtp_check():
    """Kiểm tra kết nối SMTP server."""
    try:
        data = request.json
        server = data.get('smtp_server', 'smtp.gmail.com')
        port = int(data.get('smtp_port', 587))
        user_email = data['email']
        password = data['password']
        
        add_log('smtp', 'C', f'--- Kiểm tra kết nối SMTP ---')
        smtp = LoggedSMTP(server, port)
        smtp.ehlo()
        smtp.starttls()
        smtp.ehlo()
        smtp.login(user_email, password)
        smtp.noop()
        smtp.quit()
        add_log('smtp', 'S', '--- Kết nối SMTP thành công! ---')
        
        return jsonify({
            'success': True,
            'message': f'Kết nối SMTP tới {server}:{port} thành công!'
        })
    except Exception as e:
        add_log('smtp', 'S', f'LỖI: {str(e)}')
        return jsonify({
            'success': False,
            'message': f'Không thể kết nối SMTP: {str(e)}'
        }), 500


@app.route('/api/pop3/fetch', methods=['POST'])
def pop3_fetch():
    """Nhận danh sách email qua giao thức POP3.
    
    Body JSON:
        pop3_server, pop3_port, email, password
    
    Returns:
        Danh sách email với header cơ bản
    """
    try:
        data = request.json
        server = data.get('pop3_server', 'pop.gmail.com')
        port = int(data.get('pop3_port', 995))
        user_email = data['email']
        password = data['password']
        
        add_log('pop3', 'C', f'--- Bắt đầu nhận thư ---')
        
        pop3 = LoggedPOP3(server, port)
        pop3.user(user_email)
        pop3.pass_(password)
        
        # Lấy thống kê
        count, total_size = pop3.stat()
        add_log('pop3', 'S', f'Có {count} thư, tổng {total_size} bytes')
        
        # Lấy danh sách
        resp, listings, _ = pop3.list()
        
        emails = []
        # Lấy tối đa 20 email mới nhất
        max_fetch = min(count, 20)
        for i in range(count, max(count - max_fetch, 0), -1):
            try:
                # Dùng TOP để lấy header (nhanh hơn RETR)
                resp, lines, octets = pop3.top(i, 5)
                parsed = parse_email_message(lines)
                parsed['id'] = i
                parsed['size'] = 0
                # Lấy size từ LIST
                for item in listings:
                    parts = item.decode('utf-8', errors='replace').split()
                    if len(parts) >= 2 and parts[0] == str(i):
                        parsed['size'] = int(parts[1])
                        break
                emails.append(parsed)
            except Exception as e:
                add_log('pop3', 'S', f'Lỗi đọc thư #{i}: {str(e)}')
        
        pop3.quit()
        
        return jsonify({
            'success': True,
            'count': count,
            'total_size': total_size,
            'emails': emails
        })
        
    except poplib.error_proto as e:
        add_log('pop3', 'S', f'LỖI POP3: {str(e)}')
        return jsonify({
            'success': False,
            'message': f'Lỗi POP3: {str(e)}'
        }), 500
    except Exception as e:
        add_log('pop3', 'S', f'LỖI: {str(e)}')
        return jsonify({
            'success': False,
            'message': f'Lỗi: {str(e)}'
        }), 500


@app.route('/api/pop3/retr/<int:msg_id>', methods=['POST'])
def pop3_retr(msg_id):
    """Tải nội dung đầy đủ một email qua POP3 RETR.
    
    Args:
        msg_id: Số thứ tự email trên server
    """
    try:
        data = request.json
        server = data.get('pop3_server', 'pop.gmail.com')
        port = int(data.get('pop3_port', 995))
        user_email = data['email']
        password = data['password']
        
        pop3 = LoggedPOP3(server, port)
        pop3.user(user_email)
        pop3.pass_(password)
        
        resp, lines, octets = pop3.retr(msg_id)
        parsed = parse_email_message(lines)
        parsed['id'] = msg_id
        
        pop3.quit()
        
        return jsonify({
            'success': True,
            'email': parsed
        })
        
    except Exception as e:
        add_log('pop3', 'S', f'LỖI RETR: {str(e)}')
        return jsonify({
            'success': False,
            'message': f'Lỗi: {str(e)}'
        }), 500


@app.route('/api/pop3/check', methods=['POST'])
def pop3_check():
    """Kiểm tra kết nối POP3 server."""
    try:
        data = request.json
        server = data.get('pop3_server', 'pop.gmail.com')
        port = int(data.get('pop3_port', 995))
        user_email = data['email']
        password = data['password']
        
        add_log('pop3', 'C', f'--- Kiểm tra kết nối POP3 ---')
        
        pop3 = LoggedPOP3(server, port)
        pop3.user(user_email)
        pop3.pass_(password)
        
        count, size = pop3.stat()
        pop3.noop()
        pop3.quit()
        
        add_log('pop3', 'S', '--- Kết nối POP3 thành công! ---')
        
        size_kb = round(size / 1024, 1)
        return jsonify({
            'success': True,
            'message': f'Kết nối POP3 thành công! Có {count} thư ({size_kb} KB)',
            'count': count,
            'size': size,
            'size_kb': size_kb
        })
    except Exception as e:
        add_log('pop3', 'S', f'LỖI: {str(e)}')
        return jsonify({
            'success': False,
            'message': f'Không thể kết nối POP3: {str(e)}'
        }), 500


@app.route('/api/logs', methods=['GET'])
def get_logs():
    """Lấy nhật ký giao thức."""
    log_type = request.args.get('type', 'all')
    if log_type == 'all':
        return jsonify({'logs': protocol_logs})
    else:
        filtered = [l for l in protocol_logs if l['type'] == log_type]
        return jsonify({'logs': filtered})


@app.route('/api/logs/clear', methods=['POST'])
def clear_logs():
    """Xóa toàn bộ nhật ký giao thức."""
    protocol_logs.clear()
    return jsonify({'success': True, 'message': 'Đã xóa nhật ký!'})


# ============================================================
# Chạy ứng dụng
# ============================================================
if __name__ == '__main__':
    print("=" * 50)
    print("  SMTP/POP3 Email Client")
    print("  URL: http://localhost:5000")
    print("=" * 50)
    app.run(debug=True, port=5000)
