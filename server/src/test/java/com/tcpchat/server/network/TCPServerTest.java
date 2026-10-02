package com.tcpchat.server.network;

import com.tcpchat.server.db.DatabaseManager;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class TCPServerTest {

    private static final String H2_DRIVER = "org.h2.Driver";

    private DatabaseManager dbManager;
    private String h2Url;

    @BeforeEach
    void setUp() throws Exception {
        h2Url = "jdbc:h2:mem:tcp_server_test_" + java.util.UUID.randomUUID().toString() + ";DB_CLOSE_DELAY=-1;MODE=MySQL";
        dbManager = DatabaseManager.getInstance();
        dbManager.initPool(h2Url, "sa", "", H2_DRIVER, 3);

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
    }

    @AfterEach
    void tearDown() throws Exception {
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

    @Test
    @DisplayName("Server khởi động, accept connection, shutdown sạch sẽ")
    void testStartAcceptAndShutdown() throws Exception {
        // Dùng port tùy chỉnh để tránh xung đột
        TCPServer server = new TCPServer(dbManager, 0); // 0 = OS chọn port

        // Start server trong thread riêng
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> serverFuture = executor.submit(server::start);

        // Chờ server sẵn sàng
        Thread.sleep(500);
        assertTrue(server.isRunning());

        int port = server.getPort();
        assertTrue(port > 0, "Server phải bind được port");

        // Connect client
        Socket client = new Socket("localhost", port);
        assertTrue(client.isConnected());

        // Wait for server to pick up connection
        long endTime = System.currentTimeMillis() + 1000;
        while (server.getActiveConnections() < 1 && System.currentTimeMillis() < endTime) {
            Thread.sleep(10);
        }
        assertEquals(1, server.getActiveConnections(), "Server did not pick up the connection");

        // Gửi key exchange
        PrintWriter out = new PrintWriter(
                new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);
        out.println("{\"type\":\"KEY_EXCHANGE\",\"key\":3}");
        Thread.sleep(200);

        // Cleanup
        client.close();
        server.shutdown();

        // Chờ server dừng
        try {
            serverFuture.get(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }

        assertFalse(server.isRunning());
        executor.shutdownNow();
    }

    @Test
    @DisplayName("Nhiều client kết nối đồng thời")
    void testMultipleClientsConnect() throws Exception {
        TCPServer server = new TCPServer(dbManager, 0);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(server::start);
        Thread.sleep(500);

        int port = server.getPort();
        int clientCount = 5;
        Socket[] clients = new Socket[clientCount];

        // Connect nhiều clients
        for (int i = 0; i < clientCount; i++) {
            clients[i] = new Socket("localhost", port);
            assertTrue(clients[i].isConnected());
        }

        // Wait for server to pick up connections
        long endTime = System.currentTimeMillis() + 1500;
        while (server.getTaskCount() < clientCount && System.currentTimeMillis() < endTime) {
            Thread.sleep(10);
        }
        assertEquals(clientCount, server.getTaskCount(), "Server did not pick up all connections");

        // Cleanup
        for (Socket c : clients) {
            if (c != null && !c.isClosed()) c.close();
        }

        server.shutdown();
        executor.shutdownNow();
    }

    @Test
    @DisplayName("Reject kết nối khi vượt quá queue size và max pool size")
    void testRejectedExecutionException() throws Exception {
        TCPServer server = new TCPServer(dbManager, 0);
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(server::start);
        Thread.sleep(500);

        int port = server.getPort();
        // ServerConfig.MAX_POOL_SIZE = 50, queue = 100 => Total capacity = 150
        int clientCount = 160; 
        Socket[] clients = new Socket[clientCount];
        
        int successfulConnections = 0;
        int failedConnections = 0;

        for (int i = 0; i < clientCount; i++) {
            try {
                clients[i] = new Socket("localhost", port);
                // Give the server a tiny amount of time to reject
                Thread.sleep(5); 
                
                // If the socket was closed by the server (due to rejection), an IO exception will occur when trying to read/write, 
                // but we can also just wait and check getTaskCount().
                successfulConnections++;
            } catch (Exception e) {
                failedConnections++;
            }
        }
        
        // Wait for server to process all accepted connections
        Thread.sleep(1500);
        
        // The server should have at most 150 tasks (max pool size + queue size)
        assertTrue(server.getTaskCount() <= 150, "Task count should not exceed max capacity (150)");

        // Cleanup
        for (Socket c : clients) {
            if (c != null && !c.isClosed()) c.close();
        }

        server.shutdown();
        executor.shutdownNow();
    }
}
