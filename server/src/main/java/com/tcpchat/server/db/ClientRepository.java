package com.tcpchat.server.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Repository quản lý thông tin các Client đã kết nối vào Hệ thống.
 * <p>
 * <b>Yêu cầu bảo mật:</b> 100% sử dụng {@link PreparedStatement} phòng tránh SQL Injection.
 * </p>

 * @author Nguyễn Quang Anh (Database & Storage)
 */
public class ClientRepository {

    private static final Logger logger = LoggerFactory.getLogger(ClientRepository.class);
    private final DatabaseManager dbManager;

    public ClientRepository() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public ClientRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * DTO đại diện cho thông tin Client trong CSDL.
     */
    public static class ClientRecord {
        private int clientId;
        private String ipAddress;
        private int port;
        private String status;
        private Timestamp connectedAt;
        private Timestamp lastActiveAt;

        public ClientRecord() {
        }

        public ClientRecord(int clientId, String ipAddress, int port, String status, Timestamp connectedAt, Timestamp lastActiveAt) {
            this.clientId = clientId;
            this.ipAddress = ipAddress;
            this.port = port;
            this.status = status;
            this.connectedAt = connectedAt;
            this.lastActiveAt = lastActiveAt;
        }

        public int getClientId() { return clientId; }
        public void setClientId(int clientId) { this.clientId = clientId; }
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Timestamp getConnectedAt() { return connectedAt; }
        public void setConnectedAt(Timestamp connectedAt) { this.connectedAt = connectedAt; }
        public Timestamp getLastActiveAt() { return lastActiveAt; }
        public void setLastActiveAt(Timestamp lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    }

    /**
     * Thêm mới thông tin Client hoặc cập nhật trạng thái nếu đã tồn tại.
     *
     * @param ipAddress IP của Client
     * @param port      Port của Client
     * @param status    Trạng thái (CONNECTED, DISCONNECTED, v.v.)
     * @return true nếu ghi/cập nhật thành công
     */
    public boolean saveOrUpdateClient(String ipAddress, int port, String status) {
        String insertSql = "INSERT INTO clients (ip_address, port, status) VALUES (?, ?, ?)";
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = dbManager.getConnection();
            stmt = conn.prepareStatement(insertSql);
            stmt.setString(1, ipAddress);
            stmt.setInt(2, port);
            stmt.setString(3, status != null ? status : "CONNECTED");

            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (Exception e) {
            logger.error("Lỗi khi lưu thông tin Client {}:{}", ipAddress, port, e);
            return false;
        } finally {
            closeQuietly(stmt);
            if (conn != null) {
                dbManager.releaseConnection(conn);
            }
        }
    }

    /**
     * Cập nhật trạng thái của Client (kết nối / ngắt kết nối).
     *
     * @param ipAddress IP Client
     * @param status    Trạng thái mới
     * @return true nếu thành công
     */
    public boolean updateClientStatus(String ipAddress, String status) {
        String sql = "UPDATE clients SET status = ?, last_active_at = CURRENT_TIMESTAMP WHERE ip_address = ?";
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = dbManager.getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, status);
            stmt.setString(2, ipAddress);

            int updated = stmt.executeUpdate();
            return updated > 0;
        } catch (Exception e) {
            logger.error("Lỗi khi cập nhật trạng thái Client IP = {}", ipAddress, e);
            return false;
        } finally {
            closeQuietly(stmt);
            if (conn != null) {
                dbManager.releaseConnection(conn);
            }
        }
    }

    /**
     * Truy vấn thông tin Client gần nhất theo IP.
     *
     * @param ipAddress IP Client
     * @return ClientRecord hoặc null nếu không tồn tại
     */
    public ClientRecord getClientByIp(String ipAddress) {
        String sql = "SELECT client_id, ip_address, port, status, connected_at, last_active_at FROM clients WHERE ip_address = ? ORDER BY client_id DESC LIMIT 1";
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dbManager.getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, ipAddress);

            rs = stmt.executeQuery();
            if (rs.next()) {
                return new ClientRecord(
                        rs.getInt("client_id"),
                        rs.getString("ip_address"),
                        rs.getInt("port"),
                        rs.getString("status"),
                        rs.getTimestamp("connected_at"),
                        rs.getTimestamp("last_active_at")
                );
            }
        } catch (Exception e) {
            logger.error("Lỗi khi tìm kiếm Client IP = {}", ipAddress, e);
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
            if (conn != null) {
                dbManager.releaseConnection(conn);
            }
        }
        return null;
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
