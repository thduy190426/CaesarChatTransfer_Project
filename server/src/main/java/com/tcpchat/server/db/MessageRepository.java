package com.tcpchat.server.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository xử lý các thao tác dữ liệu tin nhắn và tần suất ký tự.
 * <p>
 * <b>Yêu cầu bảo mật:</b> 100% sử dụng {@link PreparedStatement} cho mọi truy vấn SQL để phòng chống tấn công SQL Injection.
 * </p>

 * @author Nguyễn Quang Anh (Database & Storage)
 */
public class MessageRepository {

    private static final Logger logger = LoggerFactory.getLogger(MessageRepository.class);
    private final DatabaseManager dbManager;

    public MessageRepository() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public MessageRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * DTO chứa dữ liệu Record tin nhắn từ CSDL.
     */
    public static class MessageRecord {
        private long id;
        private String clientIp;
        private String cipherText;
        private int shiftKey;
        private String plainText;
        private Timestamp createdAt;

        public MessageRecord() {
        }

        public MessageRecord(long id, String clientIp, String cipherText, int shiftKey, String plainText, Timestamp createdAt) {
            this.id = id;
            this.clientIp = clientIp;
            this.cipherText = cipherText;
            this.shiftKey = shiftKey;
            this.plainText = plainText;
            this.createdAt = createdAt;
        }

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getClientIp() { return clientIp; }
        public void setClientIp(String clientIp) { this.clientIp = clientIp; }
        public String getCipherText() { return cipherText; }
        public void setCipherText(String cipherText) { this.cipherText = cipherText; }
        public int getShiftKey() { return shiftKey; }
        public void setShiftKey(int shiftKey) { this.shiftKey = shiftKey; }
        public String getPlainText() { return plainText; }
        public void setPlainText(String plainText) { this.plainText = plainText; }
        public Timestamp getCreatedAt() { return createdAt; }
        public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    }

    /**
     * Lưu tin nhắn đã mã hóa / giải mã cùng bảng tần suất ký tự vào CSDL.
     * <p>Thao tác được thực hiện trong 1 Transaction nguyên tố (Atomic Transaction).</p>
     *
     * @param clientIp        IP của Client gửi
     * @param cipherText      Văn bản mã hóa
     * @param shiftKey        Dịch khóa Caesar (1-25)
     * @param plainText       Văn bản gốc đã giải mã
     * @param charFrequencies Map tần suất xuất hiện ký tự
     * @return ID của tin nhắn vừa tạo trong DB, hoặc -1 nếu thất bại
     */
    public long saveMessage(String clientIp, String cipherText, int shiftKey, String plainText, Map<Character, Integer> charFrequencies) {
        String insertMessageSql = "INSERT INTO messages (client_ip, cipher_text, shift_key, plain_text) VALUES (?, ?, ?, ?)";
        String insertFreqSql = "INSERT INTO char_frequencies (message_id, ch, frequency) VALUES (?, ?, ?)";

        Connection conn = null;
        PreparedStatement msgStmt = null;
        PreparedStatement freqStmt = null;
        ResultSet generatedKeys = null;
        long messageId = -1;

        try {
            conn = dbManager.getConnection();
            conn.setAutoCommit(false); // Bắt đầu Transaction

            // 1. Lưu bản ghi tin nhắn bằng PreparedStatement
            msgStmt = conn.prepareStatement(insertMessageSql, Statement.RETURN_GENERATED_KEYS);
            msgStmt.setString(1, clientIp);
            msgStmt.setString(2, cipherText);
            msgStmt.setInt(3, shiftKey);
            msgStmt.setString(4, plainText);

            int affectedRows = msgStmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Lưu tin nhắn thất bại, không bản ghi nào được thêm vào.");
            }

            generatedKeys = msgStmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                messageId = generatedKeys.getLong(1);
            } else {
                throw new SQLException("Lưu tin nhắn thất bại, không lấy được ID tự tăng.");
            }

            // 2. Lưu tần suất ký tự kèm theo tin nhắn bằng PreparedStatement Batching
            if (charFrequencies != null && !charFrequencies.isEmpty()) {
                freqStmt = conn.prepareStatement(insertFreqSql);
                for (Map.Entry<Character, Integer> entry : charFrequencies.entrySet()) {
                    freqStmt.setLong(1, messageId);
                    freqStmt.setString(2, String.valueOf(entry.getKey()));
                    freqStmt.setInt(3, entry.getValue());
                    freqStmt.addBatch();
                }
                freqStmt.executeBatch();
            }

            conn.commit(); // Cam kết Transaction
            logger.info("Đã lưu tin nhắn thành công với Message ID: {}", messageId);

        } catch (Exception e) {
            logger.error("Lỗi khi lưu tin nhắn vào CSDL, đang thực hiện rollback...", e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("Rollback thất bại: {}", ex.getMessage());
                }
            }
            messageId = -1;
        } finally {
            closeQuietly(generatedKeys);
            closeQuietly(msgStmt);
            closeQuietly(freqStmt);
            if (conn != null) {
                dbManager.releaseConnection(conn);
            }
        }

        return messageId;
    }

    /**
     * Tìm kiếm tin nhắn theo ID.
     *
     * @param id Message ID
     * @return MessageRecord hoặc null nếu không tìm thấy
     */
    public MessageRecord getMessageById(long id) {
        String sql = "SELECT id, client_ip, cipher_text, shift_key, plain_text, created_at FROM messages WHERE id = ?";
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dbManager.getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, id);

            rs = stmt.executeQuery();
            if (rs.next()) {
                return new MessageRecord(
                        rs.getLong("id"),
                        rs.getString("client_ip"),
                        rs.getString("cipher_text"),
                        rs.getInt("shift_key"),
                        rs.getString("plain_text"),
                        rs.getTimestamp("created_at")
                );
            }
        } catch (Exception e) {
            logger.error("Lỗi khi lấy tin nhắn có ID = {}", id, e);
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
            if (conn != null) {
                dbManager.releaseConnection(conn);
            }
        }
        return null;
    }

    /**
     * Lấy danh sách tất cả tin nhắn đã lưu trong CSDL.
     *
     * @return Danh sách các MessageRecord
     */
    public List<MessageRecord> getAllMessages() {
        String sql = "SELECT id, client_ip, cipher_text, shift_key, plain_text, created_at FROM messages ORDER BY id DESC";
        List<MessageRecord> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dbManager.getConnection();
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();

            while (rs.next()) {
                list.add(new MessageRecord(
                        rs.getLong("id"),
                        rs.getString("client_ip"),
                        rs.getString("cipher_text"),
                        rs.getInt("shift_key"),
                        rs.getString("plain_text"),
                        rs.getTimestamp("created_at")
                ));
            }
        } catch (Exception e) {
            logger.error("Lỗi khi lấy danh sách tin nhắn", e);
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
            if (conn != null) {
                dbManager.releaseConnection(conn);
            }
        }
        return list;
    }

    /**
     * Lấy bảng tần suất ký tự tương ứng với một tin nhắn.
     *
     * @param messageId ID tin nhắn
     * @return Map ký tự -> số lần xuất hiện
     */
    public Map<Character, Integer> getCharFrequenciesByMessageId(long messageId) {
        String sql = "SELECT ch, frequency FROM char_frequencies WHERE message_id = ?";
        Map<Character, Integer> map = new HashMap<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dbManager.getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, messageId);

            rs = stmt.executeQuery();
            while (rs.next()) {
                String chStr = rs.getString("ch");
                if (chStr != null && !chStr.isEmpty()) {
                    map.put(chStr.charAt(0), rs.getInt("frequency"));
                }
            }
        } catch (Exception e) {
            logger.error("Lỗi khi truy vấn tần suất ký tự cho Message ID = {}", messageId, e);
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
            if (conn != null) {
                dbManager.releaseConnection(conn);
            }
        }
        return map;
    }

    private void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception ignored) {
            }
        }
    }
}
