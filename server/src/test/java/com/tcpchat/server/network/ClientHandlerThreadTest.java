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
        
        dbManager = DatabaseManager.getInstance();
        dbManager.initPool(H2_URL, "sa", "", H2_DRIVER, 3);

        
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

        
        testServerSocket = new ServerSocket(0);
        executor = Executors.newSingleThreadExecutor();

        
        new File(ServerConfig.UPLOAD_DIR).mkdirs();
    }

    @AfterEach
    void tearDown() throws Exception {
        executor.shutdownNow();
        testServerSocket.close();

        
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

    
    private ClientStreams connectClient() throws Exception {
        int port = testServerSocket.getLocalPort();

        
        executor.submit(() -> {
            try {
                Socket serverSideSocket = testServerSocket.accept();
                new ClientHandlerThread(serverSideSocket, dbManager).run();
            } catch (Exception ignored) {
            }
        });

        
        Socket clientSocket = new Socket("localhost", port);
        clientSocket.setSoTimeout(5000); 
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
            
            Message keyMsg = new Message(Protocol.TYPE_KEY_EXCHANGE);
            keyMsg.setKey(3);
            client.out.println(GSON.toJson(keyMsg));

            
            Message textMsg = new Message(Protocol.TYPE_TEXT);
            textMsg.setCipherText("khoor");
            client.out.println(GSON.toJson(textMsg));

            
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
            
            Message textMsg = new Message(Protocol.TYPE_TEXT);
            textMsg.setCipherText("khoor");
            client.out.println(GSON.toJson(textMsg));

            
            String response = client.in.readLine();
            assertNotNull(response);
            assertTrue(response.contains("ERROR"), "Phải nhận ERROR khi chưa exchange key");
        }
    }

    @Test
    @DisplayName("Gửi JSON malformed → nhận ERROR, handler không crash")
    void testMalformedJson_receivesErrorAndContinues() throws Exception {
        try (ClientStreams client = connectClient()) {
            
            client.out.println("this is not json at all!!!");

            
            String errorResponse = client.in.readLine();
            assertNotNull(errorResponse);
            assertTrue(errorResponse.contains("ERROR"));

            
            Message keyMsg = new Message(Protocol.TYPE_KEY_EXCHANGE);
            keyMsg.setKey(5);
            client.out.println(GSON.toJson(keyMsg));

            
            Message textMsg = new Message(Protocol.TYPE_TEXT);
            textMsg.setCipherText("mjqqt");  
            client.out.println(GSON.toJson(textMsg));

            
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
            
            byte[] fileContent = "Hello, this is test file content!".getBytes(StandardCharsets.UTF_8);
            String fileName = "test_upload.txt";

            
            FilePacket header = new FilePacket(Protocol.TYPE_FILE);
            header.setFileName(fileName);
            header.setFileSize((long) fileContent.length);
            header.setMimeType("text/plain");
            client.out.println(GSON.toJson(header));

            
            DataOutputStream dataOut = new DataOutputStream(client.socket.getOutputStream());
            dataOut.write(fileContent);
            dataOut.flush();

            
            String response = client.in.readLine();
            assertNotNull(response, "Server phải gửi FILE_ACK");
            assertTrue(response.contains("FILE_ACK"), "Response phải là FILE_ACK");

            
            Path uploadDir = Path.of(ServerConfig.UPLOAD_DIR);
            assertTrue(Files.exists(uploadDir), "Thư mục uploads phải tồn tại");
            
            boolean fileFound;
            try (java.util.stream.Stream<Path> stream = Files.list(uploadDir)) {
                fileFound = stream.anyMatch(p -> p.getFileName().toString().contains("test_upload"));
            }
            assertTrue(fileFound, "File phải được lưu trong thư mục uploads");

            
            try (java.util.stream.Stream<Path> stream = Files.list(uploadDir)) {
                stream.filter(p -> p.getFileName().toString().contains("test_upload"))
                      .forEach(p -> p.toFile().delete());
            }
        }
    }
}
