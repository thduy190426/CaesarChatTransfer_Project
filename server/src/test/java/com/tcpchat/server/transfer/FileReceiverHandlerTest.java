package com.tcpchat.server.transfer;

import com.tcpchat.common.MessageParser;
import com.tcpchat.common.Protocol;
import com.tcpchat.common.model.FilePacket;
import com.tcpchat.server.db.DatabaseManager;
import com.tcpchat.server.db.FileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class FileReceiverHandlerTest {

    private static final String H2_URL = "jdbc:h2:mem:file_receiver_test;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String H2_DRIVER = "org.h2.Driver";
    private DatabaseManager dbManager;
    private FileRepository fileRepo;

    @BeforeEach
    public void setup() throws Exception {
        File uploadDir = new File("uploads");
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        // Cấu hình H2 database để test an toàn không phụ thuộc MySQL
        dbManager = DatabaseManager.getInstance();
        dbManager.initPool(H2_URL, "sa", "", H2_DRIVER, 3);
        fileRepo = new FileRepository(dbManager);

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
    public void cleanup() {
        File uploadDir = new File("uploads");
        if (uploadDir.exists()) {
            File[] files = uploadDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
        if (dbManager != null) {
            dbManager.closePool();
        }
    }

    @Test
    @DisplayName("Nhận file thành công: Lưu đĩa, lưu DB trạng thái SUCCESS, gửi trả FILE_ACK")
    public void testReceiveFileSuccess() throws IOException {
        FilePacket header = new FilePacket(Protocol.TYPE_FILE);
        header.setFileName("dummy.txt");
        header.setFileSize(5L);
        header.setMimeType("text/plain");

        String jsonHeader = MessageParser.toJson(header);
        byte[] fileData = "Hello".getBytes(StandardCharsets.UTF_8);
        
        ByteArrayInputStream in = new ByteArrayInputStream(fileData);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        FileReceiverHandler handler = new FileReceiverHandler(in, out, "127.0.0.1");
        handler.receiveFile(jsonHeader);

        String response = out.toString(StandardCharsets.UTF_8);
        assertTrue(response.contains("\"type\":\"FILE_ACK\""));
        assertTrue(response.contains("\"status\":\"OK\""));
        
        File uploadDir = new File("uploads");
        File[] savedFiles = uploadDir.listFiles((dir, name) -> name.endsWith("_dummy.txt"));
        
        assertNotNull(savedFiles);
        assertEquals(1, savedFiles.length);
        assertEquals(5L, savedFiles[0].length());

        // Kiểm tra database
        var transfers = fileRepo.getAllFileTransfers();
        assertFalse(transfers.isEmpty());
        assertEquals("SUCCESS", transfers.get(0).getStatus());
    }

    @Test
    @DisplayName("Client ngắt kết nối giữa chừng: Tự động xóa file rác (delete temp file) và cập nhật DB status = FAILED")
    public void testInterruptedFileTransfer_CleansUpPartialFileAndMarksFailed() {
        FilePacket header = new FilePacket(Protocol.TYPE_FILE);
        header.setFileName("interrupted_test.dat");
        header.setFileSize(10000L); // Khai báo 10,000 bytes
        header.setMimeType("application/octet-stream");

        String jsonHeader = MessageParser.toJson(header);
        
        // Client chỉ gửi 500 bytes rồi đột ngột đứt kết nối (stream kết thúc)
        byte[] partialData = new byte[500];
        ByteArrayInputStream in = new ByteArrayInputStream(partialData);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        FileReceiverHandler handler = new FileReceiverHandler(in, out, "127.0.0.1");
        handler.receiveFile(jsonHeader);

        // 1. Phản hồi phải là TYPE_ERROR
        String response = out.toString(StandardCharsets.UTF_8);
        assertTrue(response.contains(Protocol.TYPE_ERROR), "Phải phản hồi thông báo ERROR cho client");

        // 2. File tạm trong uploads/ phải bị XÓA SẠCH
        File uploadDir = new File("uploads");
        File[] remainingFiles = uploadDir.listFiles((dir, name) -> name.contains("interrupted_test.dat"));
        assertNotNull(remainingFiles);
        assertEquals(0, remainingFiles.length, "File tạm dở dang phải bị xóa sạch khỏi thư mục uploads");

        // 3. Database phải cập nhật status FAILED
        var transfers = fileRepo.getAllFileTransfers();
        assertFalse(transfers.isEmpty());
        assertEquals("FAILED", transfers.get(0).getStatus(), "Trạng thái trong DB phải là FAILED");
    }

    @Test
    @DisplayName("Header không hợp lệ hoặc thiếu fileSize: Bỏ qua và không tạo file rác")
    public void testInvalidHeader_IgnoredSafely() {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        FileReceiverHandler handler = new FileReceiverHandler(in, out, "127.0.0.1");
        
        // Gửi JSON không có fileSize
        handler.receiveFile("{\"type\":\"FILE\",\"fileName\":\"bad.txt\"}");

        File uploadDir = new File("uploads");
        File[] files = uploadDir.listFiles((dir, name) -> name.contains("bad.txt"));
        assertNotNull(files);
        assertEquals(0, files.length, "Không được tạo file nếu header không hợp lệ");
    }
}
