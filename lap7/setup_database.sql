-- ============================================================
-- DATABASE SCRIPT CHO ỨNG DỤNG SMTP/POP3 EMAIL CLIENT (JAVA)
-- Môn: Lập trình mạng (LTM)
-- ============================================================

CREATE DATABASE IF NOT EXISTS `mail_client_db` 
CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `mail_client_db`;

-- 1. Bảng lưu cấu hình tài khoản email
CREATE TABLE IF NOT EXISTS `accounts` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `email` VARCHAR(255) NOT NULL UNIQUE,
    `password` VARCHAR(255),
    `pop3_server` VARCHAR(255) DEFAULT 'pop.gmail.com',
    `pop3_port` INT DEFAULT 995,
    `smtp_server` VARCHAR(255) DEFAULT 'smtp.gmail.com',
    `smtp_port` INT DEFAULT 587,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Bảng lưu các thư nhận được từ POP3 (Hộp thư đến)
CREATE TABLE IF NOT EXISTS `inbox_emails` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `msg_id` INT DEFAULT 0,
    `from_name` VARCHAR(255),
    `from_email` VARCHAR(255),
    `to_email` VARCHAR(255),
    `subject` VARCHAR(500),
    `date_str` VARCHAR(100),
    `body` LONGTEXT,
    `size_bytes` INT DEFAULT 0,
    `received_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Bảng lưu các thư đã gửi đi qua SMTP (Hộp thư đi)
CREATE TABLE IF NOT EXISTS `sent_emails` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `from_email` VARCHAR(255),
    `to_email` VARCHAR(255),
    `subject` VARCHAR(500),
    `body` LONGTEXT,
    `status` VARCHAR(50) DEFAULT 'SUCCESS',
    `sent_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Bảng lưu nhật ký giao thức mạng (Protocol Logs)
CREATE TABLE IF NOT EXISTS `protocol_logs` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `log_type` VARCHAR(10),       -- 'SMTP' hoặc 'POP3'
    `direction` VARCHAR(5),       -- 'C' (Client gửi) hoặc 'S' (Server trả lời)
    `message` TEXT,
    `logged_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
