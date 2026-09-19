-- seed.sql
-- Sample Seed Data for CaesarChatTransfer System

USE caesar_chat_db;

-- Sample clients
INSERT INTO clients (ip_address, port, status) VALUES 
('127.0.0.1', 54321, 'CONNECTED'),
('192.168.1.10', 54322, 'DISCONNECTED');

-- Sample messages
INSERT INTO messages (client_ip, cipher_text, shift_key, plain_text) VALUES 
('127.0.0.1', 'KHOOR ZRUOG', 3, 'HELLO WORLD'),
('192.168.1.10', 'Khoor, wklv lv d whvw phvvdjh!', 3, 'Hello, this is a test message!');

-- Sample character frequencies (for message_id = 1)
INSERT INTO char_frequencies (message_id, ch, frequency) VALUES 
(1, 'H', 1),
(1, 'E', 1),
(1, 'L', 3),
(1, 'O', 2),
(1, 'W', 1),
(1, 'R', 1),
(1, 'D', 1);

-- Sample file transfers
INSERT INTO file_transfers (file_name, file_size, mime_type, saved_path, status) VALUES 
('document.pdf', 1048576, 'application/pdf', 'uploads/document.pdf', 'SUCCESS'),
('sample_image.png', 204800, 'image/png', 'uploads/sample_image.png', 'SUCCESS');
