package com.tcpchat.server.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử đơn vị (Unit Test) cho FileRepository.
 *
 * @author Nguyễn Quang Anh (Database & Storage)
 */
class FileRepositoryTest {

    private DatabaseManager dbManager;
    private FileRepository fileRepository;
    private static final String H2_URL = "jdbc:h2:mem:file_repo_test;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String H2_DRIVER = "org.h2.Driver";

    @BeforeEach
    void setUp() throws Exception {
        dbManager = DatabaseManager.getInstance();
        dbManager.initPool(H2_URL, "sa", "", H2_DRIVER, 5);
        fileRepository = new FileRepository(dbManager);

        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS file_transfers (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "file_name VARCHAR(255) NOT NULL, " +
                    "file_size BIGINT NOT NULL, " +
                    "mime_type VARCHAR(100), " +
                    "saved_path VARCHAR(500), " +
                    "status VARCHAR(20) DEFAULT 'SUCCESS', " +
                    "transfer_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");");

            stmt.execute("DELETE FROM file_transfers;");
        } finally {
            dbManager.releaseConnection(conn);
        }
    }

    @AfterEach
    void tearDown() {
        dbManager.closePool();
    }

    @Test
    @DisplayName("Kiểm tra lưu nhật ký truyền file và cập nhật trạng thái bằng PreparedStatement")
    void testSaveAndUpdateFileTransfer() {
        long id = fileRepository.saveFileTransfer("test.txt", 1024, "text/plain", "uploads/test.txt", "SUCCESS");
        assertTrue(id > 0);

        FileRepository.FileTransferRecord record = fileRepository.getFileTransferById(id);
        assertNotNull(record);
        assertEquals("test.txt", record.getFileName());
        assertEquals(1024, record.getFileSize());
        assertEquals("SUCCESS", record.getStatus());

        boolean updated = fileRepository.updateFileTransferStatus(id, "DELETED");
        assertTrue(updated);

        FileRepository.FileTransferRecord updatedRecord = fileRepository.getFileTransferById(id);
        assertEquals("DELETED", updatedRecord.getStatus());
    }
}
