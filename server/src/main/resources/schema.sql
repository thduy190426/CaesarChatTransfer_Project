-- schema.sql
-- Database creation script for CaesarChatTransfer System

CREATE DATABASE IF NOT EXISTS caesar_chat_db;
USE caesar_chat_db;

-- 1. Table for Client connections
CREATE TABLE IF NOT EXISTS clients (
    client_id INT AUTO_INCREMENT PRIMARY KEY,
    ip_address VARCHAR(45) NOT NULL,
    port INT NOT NULL,
    status VARCHAR(20) DEFAULT 'CONNECTED',
    connected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2. Table for Messages
CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_ip VARCHAR(45) NOT NULL,
    cipher_text TEXT NOT NULL,
    shift_key INT NOT NULL,
    plain_text TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Table for Character Frequencies
CREATE TABLE IF NOT EXISTS char_frequencies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    ch CHAR(1) NOT NULL,
    frequency INT NOT NULL,
    FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE
);

-- 4. Table for File Transfers
CREATE TABLE IF NOT EXISTS file_transfers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100),
    saved_path VARCHAR(500),
    status VARCHAR(20) DEFAULT 'SUCCESS',
    transfer_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
