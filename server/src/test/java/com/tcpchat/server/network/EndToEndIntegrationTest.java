package com.tcpchat.server.network;

import com.google.gson.Gson;
import com.tcpchat.common.Protocol;
import com.tcpchat.common.crypto.CaesarCipher;
import com.tcpchat.common.model.FilePacket;
import com.tcpchat.common.model.Message;
import com.tcpchat.common.model.TextResult;
import com.tcpchat.server.config.ServerConfig;
import com.tcpchat.server.db.DatabaseManager;
import com.tcpchat.server.db.FileRepository;
import com.tcpchat.server.db.MessageRepository;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("End-to-End Integration Test: Toàn bộ luồng từ TCP Client -> TCPServer -> Caesar Decrypt -> Char Analyzer -> DB Pool -> File Storage")
class EndToEndIntegrationTest {

    private static final String H2_DRIVER = "org.h2.Driver";
    private static final String H2_URL = "jdbc:h2:mem:e2e_test;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final Gson GSON = new Gson();

    private DatabaseManager dbManager;
    private MessageRepository messageRepo;
    private FileRepository fileRepo;
    private TCPServer server;
    private ExecutorService serverExecutor;
    private int serverPort;

    @BeforeEach
    void setUp() throws Exception {
        // 1. Khởi tạo Connection Pool Database H2 (MySQL Mode)
        dbManager = DatabaseManager.getInstance();
        dbManager.initPool(H2_URL, "sa", "", H2_DRIVER, 5);
        messageRepo = new MessageRepository(dbManager);
        fileRepo = new FileRepository(dbManager);

        // 2. Tạo Schema Database đầy đủ
        Connection conn = dbManager.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS clients ("
                    + "client_id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "ip_address VARCHAR(45) NOT NULL, "
                    + "port INT NOT NULL, "
                    + "status VARCHAR(20) DEFAULT 'CONNECTED', "
                    + "connected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "last_active_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            stmt.execute("CREATE TABLE IF NOT EXISTS messages ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "client_ip VARCHAR(45) NOT NULL, "
                    + "cipher_text TEXT NOT NULL, "
                    + "shift_key INT NOT NULL, "
                    + "plain_text TEXT NOT NULL, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            stmt.execute("CREATE TABLE IF NOT EXISTS char_frequencies ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "message_id BIGINT NOT NULL, "
                    + "ch CHAR(1) NOT NULL, "
                    + "frequency INT NOT NULL)");

            stmt.execute("CREATE TABLE IF NOT EXISTS file_transfers ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "file_name VARCHAR(255) NOT NULL, "
                    + "file_size BIGINT NOT NULL, "
                    + "mime_type VARCHAR(100), "
                    + "saved_path VARCHAR(500), "
                    + "status VARCHAR(20) DEFAULT 'SUCCESS', "
                    + "transfer_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            stmt.execute("DELETE FROM char_frequencies");
            stmt.execute("DELETE FROM file_transfers");
            stmt.execute("DELETE FROM messages");
            stmt.execute("DELETE FROM clients");
        } finally {
            dbManager.releaseConnection(conn);
        }

        // 3. Khởi tạo thư mục uploads
        new File(ServerConfig.UPLOAD_DIR).mkdirs();

        // 4. Khởi động TCPServer trên port ngẫu nhiên (port 0)
        server = new TCPServer(dbManager, 0);
        serverExecutor = Executors.newSingleThreadExecutor();
        serverExecutor.submit(server::start);

        // Chờ server bind port
        Thread.sleep(600);
        assertTrue(server.isRunning(), "Server phải ở trạng thái running");
        serverPort = server.getPort();
        assertTrue(serverPort > 0, "Server phải lấy được port hợp lệ");
    }

    @AfterEach
    void tearDown() throws Exception {
        // Tắt server
        if (server != null) {
            server.shutdown();
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }

        // Xóa bảng DB & đóng pool
        if (dbManager != null && dbManager.isInitialized()) {
            Connection conn = dbManager.getConnection();
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS char_frequencies");
                stmt.execute("DROP TABLE IF EXISTS file_transfers");
                stmt.execute("DROP TABLE IF EXISTS messages");
                stmt.execute("DROP TABLE IF EXISTS clients");
            } finally {
                dbManager.releaseConnection(conn);
            }
            dbManager.closePool();
        }

        // Dọn dẹp file tạm uploads
        File uploadDir = new File(ServerConfig.UPLOAD_DIR);
        if (uploadDir.exists()) {
            File[] files = uploadDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().contains("e2e_")) {
                        f.delete();
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Kiểm thử trọn vẹn toàn bộ luồng E2E: Kết nối -> Trao đổi khóa -> Chat mã hóa -> Giải mã & Phân tích -> Ghi DB -> Gửi File nhị phân -> Ngắt kết nối")
    void testCompleteEndToEndFlow() throws Exception {
        // --- BƯỚC 1: Client kết nối TCP tới Server ---
        Socket socket = new Socket("localhost", serverPort);
        socket.setSoTimeout(5000);
        assertTrue(socket.isConnected(), "Client phải kết nối thành công tới Server");

        PrintWriter out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());

        // --- BƯỚC 2: Trao đổi khóa Caesar (KEY_EXCHANGE = 7) ---
        int caesarKey = 7;
        Message keyExchangeMsg = new Message(Protocol.TYPE_KEY_EXCHANGE);
        keyExchangeMsg.setKey(caesarKey);
        out.println(GSON.toJson(keyExchangeMsg));

        // --- BƯỚC 3: Client mã hóa và gửi tin nhắn TEXT ---
        String plainText = "Hello World! PTIT Caesar 2026";
        String cipherText = CaesarCipher.encrypt(plainText, caesarKey);
        assertEquals("Olssv Dvysk! WAPA Jhlzhy 2026", cipherText);

        Message textMsg = new Message(Protocol.TYPE_TEXT);
        textMsg.setCipherText(cipherText);
        out.println(GSON.toJson(textMsg));

        // --- BƯỚC 4: Client nhận kết quả TEXT_RESULT từ Server ---
        String textResultJson = in.readLine();
        assertNotNull(textResultJson, "Server phải phản hồi TEXT_RESULT");

        TextResult result = GSON.fromJson(textResultJson, TextResult.class);
        assertEquals(Protocol.TYPE_TEXT_RESULT, result.getType());
        assertEquals(plainText, result.getPlainText(), "Server phải giải mã chính xác bản rõ");
        
        Map<Character, Integer> freq = result.getFrequency();
        assertNotNull(freq, "Bảng tần suất ký tự không được null");
        assertEquals(3, freq.get('l'), "Ký tự 'l' phải xuất hiện 3 lần trong 'Hello World!'");
        assertEquals(1, freq.get('H'), "Ký tự 'H' hoa phải xuất hiện 1 lần");

        // --- BƯỚC 5: Kiểm tra Database đã lưu tin nhắn và bảng tần suất ký tự ---
        Thread.sleep(200); // Chờ DB commit
        var messages = messageRepo.getAllMessages();
        assertEquals(1, messages.size(), "Database phải có đúng 1 tin nhắn được lưu");
        MessageRepository.MessageRecord savedMsg = messages.get(0);
        assertEquals(cipherText, savedMsg.getCipherText());
        assertEquals(plainText, savedMsg.getPlainText());
        assertEquals(caesarKey, savedMsg.getShiftKey());

        Map<Character, Integer> dbFreq = messageRepo.getCharFrequenciesByMessageId(savedMsg.getId());
        assertFalse(dbFreq.isEmpty(), "Bảng tần suất ký tự trong DB không được rỗng");
        assertEquals(3, dbFreq.get('l'));

        // --- BƯỚC 6: Client truyền file nhị phân (Binary Chunked Transfer) ---
        String sampleFileName = "e2e_document.pdf";
        byte[] fileBytes = "PDF-1.7 %SAMPLE E2E INTEGRATION TEST CONTENT FOR CAESAR CHAT%".getBytes(StandardCharsets.UTF_8);

        FilePacket fileHeader = new FilePacket(Protocol.TYPE_FILE);
        fileHeader.setFileName(sampleFileName);
        fileHeader.setFileSize((long) fileBytes.length);
        fileHeader.setMimeType("application/pdf");
        out.println(GSON.toJson(fileHeader));

        // Gửi dữ liệu nhị phân
        dataOut.write(fileBytes);
        dataOut.flush();

        // --- BƯỚC 7: Client nhận phản hồi FILE_ACK từ Server ---
        String fileAckJson = in.readLine();
        assertNotNull(fileAckJson, "Server phải phản hồi FILE_ACK");
        assertTrue(fileAckJson.contains("FILE_ACK"));

        // --- BƯỚC 8: Kiểm tra file trên đĩa và trong Database ---
        var fileTransfers = fileRepo.getAllFileTransfers();
        assertEquals(1, fileTransfers.size(), "Database phải có đúng 1 bản ghi truyền file");
        FileRepository.FileTransferRecord savedFileRecord = fileTransfers.get(0);
        assertEquals(sampleFileName, savedFileRecord.getFileName());
        assertEquals(fileBytes.length, savedFileRecord.getFileSize());
        assertEquals("SUCCESS", savedFileRecord.getStatus());

        // Kiểm tra file thực tế lưu trong thư mục uploads
        Path uploadedFilePath = Path.of(savedFileRecord.getSavedPath());
        assertTrue(Files.exists(uploadedFilePath), "File phải thực sự tồn tại trên ổ đĩa");
        byte[] savedBytes = Files.readAllBytes(uploadedFilePath);
        assertArrayEquals(fileBytes, savedBytes, "Nội dung file lưu trên server phải khớp 100% với file gửi đi");

        // --- BƯỚC 9: Phản hồi Heartbeat PONG ---
        Message pongMsg = new Message(Protocol.TYPE_PONG);
        out.println(GSON.toJson(pongMsg));

        // --- BƯỚC 10: Client đóng kết nối sạch sẽ ---
        socket.close();
        Thread.sleep(200);
    }
}
