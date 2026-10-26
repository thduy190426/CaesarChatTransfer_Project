package com.tcpchat.server.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class ClientRepositoryTest {

    private DatabaseManager dbManager;
    private ClientRepository clientRepository;
    private static final String H2_URL = "jdbc:h2:mem:client_repo_test;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String H2_DRIVER = "org.h2.Driver";

    @BeforeEach
    void setUp() throws Exception {
        dbManager = DatabaseManager.getInstance();
        dbManager.initPool(H2_URL, "sa", "", H2_DRIVER, 3);
        clientRepository = new ClientRepository(dbManager);

        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS clients (" +
                    "client_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "ip_address VARCHAR(45) NOT NULL, " +
                    "port INT NOT NULL, " +
                    "status VARCHAR(20) DEFAULT 'CONNECTED', " +
                    "connected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "last_active_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");");
            stmt.execute("DELETE FROM clients;");
        } finally {
            dbManager.releaseConnection(conn);
        }
    }

    @AfterEach
    void tearDown() {
        dbManager.closePool();
    }

    @Test
    @DisplayName("Lưu mới thông tin client và lấy lại theo IP")
    void testSaveAndGetClient() {
        boolean saved = clientRepository.saveOrUpdateClient("192.168.1.100", 54321, "CONNECTED");
        assertTrue(saved, "Lưu client thành công");

        ClientRepository.ClientRecord record = clientRepository.getClientByIp("192.168.1.100");
        assertNotNull(record);
        assertEquals("192.168.1.100", record.getIpAddress());
        assertEquals(54321, record.getPort());
        assertEquals("CONNECTED", record.getStatus());
        assertNotNull(record.getConnectedAt());
    }

    @Test
    @DisplayName("Cập nhật trạng thái của Client (DISCONNECTED)")
    void testUpdateClientStatus() {
        clientRepository.saveOrUpdateClient("10.0.0.5", 8080, "CONNECTED");
        
        boolean updated = clientRepository.updateClientStatus("10.0.0.5", "DISCONNECTED");
        assertTrue(updated, "Cập nhật trạng thái thành công");

        ClientRepository.ClientRecord record = clientRepository.getClientByIp("10.0.0.5");
        assertNotNull(record);
        assertEquals("DISCONNECTED", record.getStatus());
    }

    @Test
    @DisplayName("Truy vấn IP không tồn tại trả về null an toàn")
    void testGetNonExistentClient() {
        ClientRepository.ClientRecord record = clientRepository.getClientByIp("0.0.0.0");
        assertNull(record, "IP không tồn tại phải trả về null");
    }

    @Test
    @DisplayName("An toàn trước tấn công SQL Injection nhờ PreparedStatement")
    void testSqlInjectionSafety() {
        String maliciousIp = "' OR '1'='1";
        boolean saved = clientRepository.saveOrUpdateClient(maliciousIp, 9999, "CONNECTED");
        assertTrue(saved);

        ClientRepository.ClientRecord record = clientRepository.getClientByIp(maliciousIp);
        assertNotNull(record);
        assertEquals(maliciousIp, record.getIpAddress());

        // Kiểm tra không bị leak toàn bộ dữ liệu khi query IP bình thường
        ClientRepository.ClientRecord normalRecord = clientRepository.getClientByIp("127.0.0.1");
        assertNull(normalRecord);
    }
}
