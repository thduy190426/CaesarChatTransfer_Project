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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử đơn vị (Unit Test) cho lớp DatabaseManager.
 * Thử nghiệm cơ chế cấp phát Connection an toàn bằng ArrayBlockingQueue.take().
 *
 * @author Nguyễn Quang Anh (Database & Storage)
 */
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
                    conn = dbManager.getConnection(); // Chặn (take) an toàn nếu hết connection
                    assertNotNull(conn);
                    Thread.sleep(50); // Mô phỏng thao tác DB
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
}
