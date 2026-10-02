package com.tcpchat.server.network;

import com.google.gson.Gson;
import com.tcpchat.common.Protocol;
import com.tcpchat.common.model.Message;
import com.tcpchat.common.model.FilePacket;
import com.tcpchat.common.model.TextResult;
import com.tcpchat.server.config.ServerConfig;
import com.tcpchat.server.db.DatabaseManager;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ClientHandlerThreadTest {

    private static final String H2_URL = "jdbc:h2:mem:handler_test;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String H2_DRIVER = "org.h2.Driver";
    private static final Gson GSON = new Gson();

    private DatabaseManager dbManager;
    private ServerSocket testServerSocket;
    private ExecutorService executor;

    @BeforeEach
    void setUp() throws Exception {
        // 1. Setup H2 database
        dbManager = DatabaseManager.getInstance();
        dbManager.initPool(H2_URL, "sa", "", H2_DRIVER, 3);

        // Create schema
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
        } finally {
            dbManager.releaseConnection(conn);
        }

        // 2. Create test server socket (random port)
        testServerSocket = new ServerSocket(0);
        executor = Executors.newSingleThreadExecutor();

        // 3. Create uploads directory
        new File(ServerConfig.UPLOAD_DIR).mkdirs();
    }

    @AfterEach
    void tearDown() throws Exception {
        executor.shutdownNow();
        testServerSocket.close();

        // Cleanup H2 tables
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

    /**
     * Helper: connects a fake client, starts handler, returns client-side streams.
     */
    private ClientStreams connectClient() throws Exception {
        int port = testServerSocket.getLocalPort();

        // Start handler in background
        executor.submit(() -> {
            try {
                Socket serverSideSocket = testServerSocket.accept();
                new ClientHandlerThread(serverSideSocket, dbManager).run();
            } catch (Exception ignored) {
            }
        });

        // Connect client
        Socket clientSocket = new Socket("localhost", port);
        clientSocket.setSoTimeout(5000); // 5s timeout for test reads
        PrintWriter out = new PrintWriter(
                new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
        BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));

        return new ClientStreams(clientSocket, out, in);
    }

    static class ClientStreams implements AutoCloseable {
        final Socket socket;
        final PrintWriter out;
        final BufferedReader in;

        ClientStreams(Socket socket, PrintWriter out, BufferedReader in) {
            this.socket = socket;
            this.out = out;
            this.in = in;
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }

    @Test
    @DisplayName("KEY_EXCHANGE → TEXT → nhận TEXT_RESULT với plainText đúng")
    void testKeyExchangeThenTextMessage() throws Exception {
        try (ClientStreams client = connectClient()) {
            // 1. Gửi KEY_EXCHANGE
            Message keyMsg = new Message(Protocol.TYPE_KEY_EXCHANGE);
            keyMsg.setKey(3);
            client.out.println(GSON.toJson(keyMsg));

            // 2. Gửi TEXT message (cipher text "khoor" with key=3 → "hello")
            Message textMsg = new Message(Protocol.TYPE_TEXT);
            textMsg.setCipherText("khoor");
            client.out.println(GSON.toJson(textMsg));

            // 3. Đọc TEXT_RESULT response
            String response = client.in.readLine();
            assertNotNull(response, "Server phải trả về TEXT_RESULT");

            TextResult result = GSON.fromJson(response, TextResult.class);
            assertEquals("TEXT_RESULT", result.getType());
            assertEquals("hello", result.getPlainText());
            assertNotNull(result.getFrequency());
            assertTrue(result.getFrequency().size() > 0);
        }
    }

    @Test
    @DisplayName("TEXT trước khi KEY_EXCHANGE → nhận ERROR")
    void testTextBeforeKeyExchange_receivesError() throws Exception {
        try (ClientStreams client = connectClient()) {
            // Gửi TEXT mà chưa exchange key
            Message textMsg = new Message(Protocol.TYPE_TEXT);
            textMsg.setCipherText("khoor");
            client.out.println(GSON.toJson(textMsg));

            // Phải nhận ERROR
            String response = client.in.readLine();
            assertNotNull(response);
            assertTrue(response.contains("ERROR"), "Phải nhận ERROR khi chưa exchange key");
        }
    }

    @Test
    @DisplayName("Gửi JSON malformed → nhận ERROR, handler không crash")
    void testMalformedJson_receivesErrorAndContinues() throws Exception {
        try (ClientStreams client = connectClient()) {
            // 1. Gửi JSON hỏng
            client.out.println("this is not json at all!!!");

            // Phải nhận ERROR
            String errorResponse = client.in.readLine();
            assertNotNull(errorResponse);
            assertTrue(errorResponse.contains("ERROR"));

            // 2. Gửi KEY_EXCHANGE hợp lệ — handler vẫn hoạt động
            Message keyMsg = new Message(Protocol.TYPE_KEY_EXCHANGE);
            keyMsg.setKey(5);
            client.out.println(GSON.toJson(keyMsg));

            // 3. Gửi TEXT hợp lệ
            Message textMsg = new Message(Protocol.TYPE_TEXT);
            textMsg.setCipherText("mjqqt");  // key=5 → "hello"
            client.out.println(GSON.toJson(textMsg));

            // 4. Đọc TEXT_RESULT — chứng minh handler không crash
            String response = client.in.readLine();
            assertNotNull(response, "Handler phải tiếp tục hoạt động sau JSON hỏng");
            TextResult result = GSON.fromJson(response, TextResult.class);
            assertEquals("hello", result.getPlainText());
        }
    }

    @Test
    @DisplayName("Gửi FILE → Server lưu file, nhận FILE_ACK")
    void testFileTransfer_savesFileAndSendsAck() throws Exception {
        try (ClientStreams client = connectClient()) {
            // Chuẩn bị data
            byte[] fileContent = "Hello, this is test file content!".getBytes(StandardCharsets.UTF_8);
            String fileName = "test_upload.txt";

            // 1. Gửi FilePacket header
            FilePacket header = new FilePacket(Protocol.TYPE_FILE);
            header.setFileName(fileName);
            header.setFileSize((long) fileContent.length);
            header.setMimeType("text/plain");
            client.out.println(GSON.toJson(header));

            // 2. Gửi binary data
            DataOutputStream dataOut = new DataOutputStream(client.socket.getOutputStream());
            dataOut.write(fileContent);
            dataOut.flush();

            // 3. Đọc FILE_ACK
            String response = client.in.readLine();
            assertNotNull(response, "Server phải gửi FILE_ACK");
            assertTrue(response.contains("FILE_ACK"), "Response phải là FILE_ACK");

            // 4. Kiểm tra file đã được lưu
            Path uploadDir = Path.of(ServerConfig.UPLOAD_DIR);
            assertTrue(Files.exists(uploadDir), "Thư mục uploads phải tồn tại");
            // Tìm file vừa upload
            boolean fileFound;
            try (java.util.stream.Stream<Path> stream = Files.list(uploadDir)) {
                fileFound = stream.anyMatch(p -> p.getFileName().toString().contains("test_upload"));
            }
            assertTrue(fileFound, "File phải được lưu trong thư mục uploads");

            // Cleanup
            try (java.util.stream.Stream<Path> stream = Files.list(uploadDir)) {
                stream.filter(p -> p.getFileName().toString().contains("test_upload"))
                      .forEach(p -> p.toFile().delete());
            }
        }
    }
}
