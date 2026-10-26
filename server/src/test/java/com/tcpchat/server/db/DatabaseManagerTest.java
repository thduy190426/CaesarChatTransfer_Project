package com.tcpchat.server.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {

    private DatabaseManager dbManager;
    private static final String H2_URL = "jdbc:h2:mem:db_manager_test;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String H2_DRIVER = "org.h2.Driver";
    private static final int POOL_SIZE = 3;

    @BeforeEach
    void setUp() {
        dbManager = DatabaseManager.getInstance();
        dbManager.initPool(H2_URL, "sa", "", H2_DRIVER, POOL_SIZE);
    }

    @AfterEach
    void tearDown() {
        dbManager.closePool();
    }

    @Test
    @DisplayName("Kiểm tra khởi tạo Connection Pool đúng số lượng kết nối")
    void testPoolInitialization() {
        assertTrue(dbManager.isInitialized());
        assertEquals(POOL_SIZE, dbManager.getAvailableConnections());
    }

    @Test
    @DisplayName("Kiểm tra lấy connection bằng take() và trả connection về pool")
    void testGetAndReleaseConnection() throws SQLException, InterruptedException {
        assertEquals(3, dbManager.getAvailableConnections());

        Connection conn1 = dbManager.getConnection();
        assertNotNull(conn1);
        assertFalse(conn1.isClosed());
        assertEquals(2, dbManager.getAvailableConnections());

        Connection conn2 = dbManager.getConnection();
        assertNotNull(conn2);
        assertEquals(1, dbManager.getAvailableConnections());

        dbManager.releaseConnection(conn1);
        assertEquals(2, dbManager.getAvailableConnections());

        dbManager.releaseConnection(conn2);
        assertEquals(3, dbManager.getAvailableConnections());
    }

    @Test
    @DisplayName("Kiểm tra tính Thread-safe khi lấy và trả Connection đồng thời từ nhiều Thread")
    void testConcurrentConnectionAccess() throws InterruptedException, ExecutionException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                Connection conn = null;
                try {
                    conn = dbManager.getConnection(); 
                    assertNotNull(conn);
                    Thread.sleep(50); 
                    return true;
                } catch (Exception e) {
                    return false;
                } finally {
                    if (conn != null) {
                        dbManager.releaseConnection(conn);
                    }
                }
            }));
        }

        for (Future<Boolean> future : futures) {
            assertTrue(future.get(), "Thread lấy/trả connection thất bại");
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(POOL_SIZE, dbManager.getAvailableConnections(), "Sau khi toàn bộ thread chạy xong, pool phải chứa lại đủ connections");
    }

    @Test
    @DisplayName("Kiểm tra cơ chế BlockingQueue: Thread bị BLOCK khi Pool hết connection và được UNBLOCK khi có connection trả về")
    void testPoolBlockingWhenExhausted() throws Exception {
        // Pool có 3 connections. Lấy hết cả 3 connections:
        Connection conn1 = dbManager.getConnection();
        Connection conn2 = dbManager.getConnection();
        Connection conn3 = dbManager.getConnection();
        assertEquals(0, dbManager.getAvailableConnections(), "Pool phải hết connection");

        AtomicBoolean threadGotConnection = new AtomicBoolean(false);
        CountDownLatch threadStartedLatch = new CountDownLatch(1);

        ExecutorService singleExecutor = Executors.newSingleThreadExecutor();
        Future<?> future = singleExecutor.submit(() -> {
            try {
                threadStartedLatch.countDown();
                Connection connBlocked = dbManager.getConnection(); // Sẽ block ở đây
                threadGotConnection.set(true);
                dbManager.releaseConnection(connBlocked);
            } catch (Exception e) {
                // ignore
            }
        });

        assertTrue(threadStartedLatch.await(2, TimeUnit.SECONDS));
        Thread.sleep(200); // Chờ để thread chắc chắn đang bị block trong take()

        assertFalse(threadGotConnection.get(), "Thread thứ 4 phải đang bị BLOCK vì pool đã cạn");

        // Giải phóng 1 connection để unblock
        dbManager.releaseConnection(conn1);

        // Chờ thread lấy được connection
        future.get(3, TimeUnit.SECONDS);
        assertTrue(threadGotConnection.get(), "Thread thứ 4 phải được UNBLOCK sau khi có connection được trả về");

        // Cleanup
        dbManager.releaseConnection(conn2);
        dbManager.releaseConnection(conn3);
        singleExecutor.shutdownNow();
    }

    @Test
    @DisplayName("Kiểm tra tự phục hồi khi trả connection đã bị đóng (Connection Auto-Healing)")
    void testReleaseClosedConnection() throws Exception {
        Connection conn = dbManager.getConnection();
        conn.close(); // Đóng connection bên ngoài
        assertTrue(conn.isClosed());

        // Trả connection đã đóng vào pool
        dbManager.releaseConnection(conn);

        // Lấy lại connection tiếp theo -> DatabaseManager phải phát hiện và tạo mới kết nối hợp lệ
        Connection newConn = dbManager.getConnection();
        assertNotNull(newConn);
        assertFalse(newConn.isClosed());
        assertTrue(newConn.isValid(2));

        dbManager.releaseConnection(newConn);
    }
}
