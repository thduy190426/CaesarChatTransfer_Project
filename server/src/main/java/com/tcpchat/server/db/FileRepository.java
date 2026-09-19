package com.tcpchat.server.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository xử lý thông tin nhật ký truyền tải tập tin (File Transfers).
 * <p>
 * <b>Yêu cầu bảo mật:</b> 100% sử dụng {@link PreparedStatement} cho mọi truy vấn SQL.
 * </p>

 * @author Nguyễn Quang Anh (Database & Storage)
 */
public class FileRepository {

    private static final Logger logger = LoggerFactory.getLogger(FileRepository.class);
    private final DatabaseManager dbManager;

    public FileRepository() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public FileRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * DTO đại diện cho bản ghi truyền file trong CSDL.
     */
    public static class FileTransferRecord {
        private long id;
        private String fileName;
        private long fileSize;
        private String mimeType;
        private String savedPath;
        private String status;
        private Timestamp transferTime;

        public FileTransferRecord() {
        }

        public FileTransferRecord(long id, String fileName, long fileSize, String mimeType, String savedPath, String status, Timestamp transferTime) {
            this.id = id;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.mimeType = mimeType;
            this.savedPath = savedPath;
            this.status = status;
            this.transferTime = transferTime;
        }

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        public String getSavedPath() { return savedPath; }
        public void setSavedPath(String savedPath) { this.savedPath = savedPath; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Timestamp getTransferTime() { return transferTime; }
        public void setTransferTime(Timestamp transferTime) { this.transferTime = transferTime; }
    }

    /**
     * Lưu nhật ký bắt đầu/hoàn tất truyền file.
     *
     * @param fileName Tên tập tin
     * @param fileSize Kích thước tập tin (bytes)
     * @param mimeType Loại MIME
     * @param savedPath Đường dẫn lưu file trên máy chủ
     * @param status   Trạng thái (SUCCESS, FAILED, INTERRUPTED, v.v.)
     * @return ID bản ghi vừa lưu trong DB, hoặc -1 nếu có lỗi
     */
    public long saveFileTransfer(String fileName, long fileSize, String mimeType, String savedPath, String status) {
        String sql = "INSERT INTO file_transfers (file_name, file_size, mime_type, saved_path, status) VALUES (?, ?, ?, ?, ?)";
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet generatedKeys = null;
        long id = -1;

        try {
            conn = dbManager.getConnection();
            stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, fileName);
            stmt.setLong(2, fileSize);
            stmt.setString(3, mimeType);
            stmt.setString(4, savedPath);
            stmt.setString(5, status != null ? status : "SUCCESS");

            int affected = stmt.executeUpdate();
            if (affected > 0) {
                generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    id = generatedKeys.getLong(1);
                }
            }
            logger.info("Đã ghi nhật ký truyền file '{}' thành công với ID: {}", fileName, id);
        } catch (Exception e) {
            logger.error("Lỗi khi lưu bản ghi file transfer '{}'", fileName, e);
        } finally {
            closeQuietly(generatedKeys);
            closeQuietly(stmt);
            if (conn != null) {
                dbManager.releaseConnection(conn);
            }
        }
        return id;
    }

    /**
     * Cập nhật trạng thái của bản ghi truyền file (VD: Khi bị đứt mạng hoặc xóa file tạm).
     *
     * @param id     File Transfer Record ID
     * @param status Trạng thái mới
     * @return true nếu cập nhật thành công
     */
    public boolean updateFileTransferStatus(long id, String status) {
        String sql = "UPDATE file_transfers SET status = ? WHERE id = ?";
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = dbManager.getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, status);
            stmt.setLong(2, id);

            int updated = stmt.executeUpdate();
            return updated > 0;
        } catch (Exception e) {
            logger.error("Lỗi khi cập nhật trạng thái truyền file ID = {}", id, e);
            return false;
        } finally {
            closeQuietly(stmt);
            if (conn != null) {
                dbManager.releaseConnection(conn);
            }
        }
    }

    /**
     * Lấy bản ghi truyền file theo ID.
     *
     * @param id Record ID
     * @return FileTransferRecord hoặc null
     */
    public FileTransferRecord getFileTransferById(long id) {
        String sql = "SELECT id, file_name, file_size, mime_type, saved_path, status, transfer_time FROM file_transfers WHERE id = ?";
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dbManager.getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, id);

            rs = stmt.executeQuery();
            if (rs.next()) {
                return new FileTransferRecord(
                        rs.getLong("id"),
                        rs.getString("file_name"),
                        rs.getLong("file_size"),
                        rs.getString("mime_type"),
                        rs.getString("saved_path"),
                        rs.getString("status"),
                        rs.getTimestamp("transfer_time")
                );
            }
        } catch (Exception e) {
            logger.error("Lỗi khi truy vấn file transfer ID = {}", id, e);
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
     * Lấy danh sách toàn bộ file transfers.
     *
     * @return Danh sách bản ghi FileTransferRecord
     */
    public List<FileTransferRecord> getAllFileTransfers() {
        String sql = "SELECT id, file_name, file_size, mime_type, saved_path, status, transfer_time FROM file_transfers ORDER BY id DESC";
        List<FileTransferRecord> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dbManager.getConnection();
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();

            while (rs.next()) {
                list.add(new FileTransferRecord(
                        rs.getLong("id"),
                        rs.getString("file_name"),
                        rs.getLong("file_size"),
                        rs.getString("mime_type"),
                        rs.getString("saved_path"),
                        rs.getString("status"),
                        rs.getTimestamp("transfer_time")
                ));
            }
        } catch (Exception e) {
            logger.error("Lỗi khi lấy danh sách file transfers", e);
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
            if (conn != null) {
                dbManager.releaseConnection(conn);
            }
        }
        return list;
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
