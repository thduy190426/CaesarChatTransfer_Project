package com.tcpchat.server.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    @DisplayName("Truy vấn bản ghi truyền file với ID không tồn tại trả về null")
    void testGetByNonExistentId() {
        FileRepository.FileTransferRecord record = fileRepository.getFileTransferById(999999L);
        assertNull(record);
    }

    @Test
    @DisplayName("Cập nhật trạng thái cho ID không tồn tại trả về false an toàn")
    void testUpdateNonExistentId() {
        boolean updated = fileRepository.updateFileTransferStatus(999999L, "FAILED");
        assertFalse(updated);
    }

    @Test
    @DisplayName("Lưu file với mime_type là null vẫn thành công")
    void testSaveWithNullMimeType() {
        long id = fileRepository.saveFileTransfer("binary.dat", 2048, null, "uploads/binary.dat", "IN_PROGRESS");
        assertTrue(id > 0);

        FileRepository.FileTransferRecord record = fileRepository.getFileTransferById(id);
        assertNotNull(record);
        assertNull(record.getMimeType());
        assertEquals("IN_PROGRESS", record.getStatus());
    }

    @Test
    @DisplayName("Phòng chống SQL Injection trong fileName và savedPath nhờ PreparedStatement")
    void testSqlInjectionSafety() {
        String maliciousFileName = "test'; DROP TABLE file_transfers; --.txt";
        String maliciousPath = "' OR '1'='1";

        long id = fileRepository.saveFileTransfer(maliciousFileName, 500, "text/plain", maliciousPath, "SUCCESS");
        assertTrue(id > 0);

        FileRepository.FileTransferRecord record = fileRepository.getFileTransferById(id);
        assertNotNull(record);
        assertEquals(maliciousFileName, record.getFileName());
        assertEquals(maliciousPath, record.getSavedPath());

        // Xác nhận bảng file_transfers không hề bị drop
        assertNotNull(fileRepository.getAllFileTransfers());
    }
}
