package com.tcpchat.server.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử đơn vị (Unit Test) cho MessageRepository.
 * Đảm bảo mọi câu truy vấn được thực thi chính xác thông qua PreparedStatement.
 *
 * @author Nguyễn Quang Anh (Database & Storage)
 */
class MessageRepositoryTest {

    private DatabaseManager dbManager;
    private MessageRepository messageRepository;
    private static final String H2_URL = "jdbc:h2:mem:msg_repo_test;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String H2_DRIVER = "org.h2.Driver";

    @BeforeEach
    void setUp() throws Exception {
        dbManager = DatabaseManager.getInstance();
        dbManager.initPool(H2_URL, "sa", "", H2_DRIVER, 5);
        messageRepository = new MessageRepository(dbManager);

        // Khởi tạo bảng CSDL trong H2
        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS messages (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "client_ip VARCHAR(45) NOT NULL, " +
                    "cipher_text TEXT NOT NULL, " +
                    "shift_key INT NOT NULL, " +
                    "plain_text TEXT NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS char_frequencies (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "message_id BIGINT NOT NULL, " +
                    "ch CHAR(1) NOT NULL, " +
                    "frequency INT NOT NULL" +
                    ");");
        } finally {
            dbManager.releaseConnection(conn);
        }
    }

    @AfterEach
    void tearDown() {
        dbManager.closePool();
    }

    @Test
    @DisplayName("Kiểm tra lưu tin nhắn và bảng tần suất ký tự bằng PreparedStatement")
    void testSaveMessageAndFrequencies() {
        Map<Character, Integer> freqs = new HashMap<>();
        freqs.put('H', 1);
        freqs.put('E', 1);
        freqs.put('L', 2);
        freqs.put('O', 1);

        long msgId = messageRepository.saveMessage("127.0.0.1", "KHOOR", 3, "HELLO", freqs);
        assertTrue(msgId > 0, "Lưu tin nhắn phải trả về ID tự tăng > 0");

        MessageRepository.MessageRecord record = messageRepository.getMessageById(msgId);
        assertNotNull(record);
        assertEquals("127.0.0.1", record.getClientIp());
        assertEquals("KHOOR", record.getCipherText());
        assertEquals(3, record.getShiftKey());
        assertEquals("HELLO", record.getPlainText());

        Map<Character, Integer> loadedFreqs = messageRepository.getCharFrequenciesByMessageId(msgId);
        assertEquals(4, loadedFreqs.size());
        assertEquals(2, loadedFreqs.get('L'));
        assertEquals(1, loadedFreqs.get('H'));
    }

    @Test
    @DisplayName("Kiểm tra lấy danh sách toàn bộ tin nhắn")
    void testGetAllMessages() {
        messageRepository.saveMessage("10.0.0.1", "ABC", 1, "BCD", null);
        messageRepository.saveMessage("10.0.0.2", "DEF", 2, "FGH", null);

        var list = messageRepository.getAllMessages();
        assertEquals(2, list.size());
    }
}
