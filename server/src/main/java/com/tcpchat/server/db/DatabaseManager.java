package com.tcpchat.server.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Quản lý kết nối Cơ sở dữ liệu (Connection Pool Thread-safe).
 * <p>
 * Yêu cầu cốt lõi: Sử dụng {@link ArrayBlockingQueue#take()} để cấp phát Connection an toàn và chặn (block)
 * luồng gọi nếu toàn bộ Connection trong pool đang được sử dụng.
 * </p>
 *
 * @author Nguyễn Quang Anh (Database & Storage)
 */
public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;

    private static final int DEFAULT_POOL_SIZE = 5;
    private final ArrayBlockingQueue<Connection> connectionPool;
    
    private String url;
    private String username;
    private String password;
    private String driverClass;
    private int poolSize;
    private boolean initialized = false;

    /**
     * Private constructor khởi tạo DatabaseManager.
     */
    private DatabaseManager() {
        this.connectionPool = new ArrayBlockingQueue<>(DEFAULT_POOL_SIZE);
    }

    /**
     * Lấy hằng thể Singleton của DatabaseManager.
     *
     * @return Hằng thể DatabaseManager
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Khởi tạo Connection Pool từ tập tin db.properties.
     */
    public synchronized void initPool() {
        if (initialized) {
            logger.info("DatabaseManager Connection Pool đã được khởi tạo trước đó.");
            return;
        }

        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("db.properties")) {
            if (input != null) {
                props.load(input);
            }
        } catch (Exception e) {
            logger.warn("Không thể tải db.properties, sử dụng cấu hình mặc định: {}", e.getMessage());
        }

        this.driverClass = props.getProperty("db.driver", "com.mysql.cj.jdbc.Driver");
        this.url = props.getProperty("db.url", "jdbc:mysql://localhost:3306/caesar_chat_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        this.username = props.getProperty("db.username", "root");
        this.password = props.getProperty("db.password", "root");
        this.poolSize = Integer.parseInt(props.getProperty("db.pool.size", String.valueOf(DEFAULT_POOL_SIZE)));

        initPool(this.url, this.username, this.password, this.driverClass, this.poolSize);
    }

    /**
     * Khởi tạo Connection Pool tùy chỉnh bằng tham số đầu vào.
     *
     * @param url         Địa chỉ JDBC
     * @param username    Tên đăng nhập DB
     * @param password    Mật khẩu DB
     * @param driverClass Tên JDBC Driver class
     * @param poolSize    Kích thước Pool (Số lượng connection)
     */
    public synchronized void initPool(String url, String username, String password, String driverClass, int poolSize) {
        if (initialized) {
            closePool();
        }

        this.url = url;
        this.username = username;
        this.password = password;
        this.driverClass = driverClass;
        this.poolSize = poolSize > 0 ? poolSize : DEFAULT_POOL_SIZE;

        try {
            Class.forName(this.driverClass);
        } catch (ClassNotFoundException e) {
            logger.error("Không tìm thấy JDBC Driver class: {}", this.driverClass, e);
            throw new RuntimeException("Chưa nạp được JDBC Driver: " + this.driverClass, e);
        }

        logger.info("Đang khởi tạo Database Connection Pool (size={}) tới URL: {}", this.poolSize, this.url);

        for (int i = 0; i < this.poolSize; i++) {
            try {
                Connection conn = createNewConnection();
                connectionPool.offer(conn);
            } catch (SQLException e) {
                logger.error("Lỗi khi tạo connection thứ {}/{}", i + 1, this.poolSize, e);
            }
        }

        this.initialized = true;
        logger.info("Đã khởi tạo xong Connection Pool với {} kết nối sẵn sàng.", connectionPool.size());
    }

    /**
     * Tạo mới một java.sql.Connection.
     *
     * @return Connection mới
     * @throws SQLException Nếu có lỗi kết nối JDBC
     */
    private Connection createNewConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    /**
     * Lấy một Connection từ Pool.
     * <p>
     * <b>CRITICAL:</b> Phương thức này sử dụng {@link ArrayBlockingQueue#take()} để lấy connection an toàn.
     * Nếu không có connection rảnh trong pool, thread gọi sẽ bị block cho đến khi có connection được thu hồi.
     * </p>
     *
     * @return java.sql.Connection
     * @throws SQLException         Nếu có lỗi thao tác kết nối
     * @throws InterruptedException  Nếu thread bị ngắt khi đang chờ (take)
     */
    public Connection getConnection() throws SQLException, InterruptedException {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    initPool();
                }
            }
        }

        // Đòi hỏi của đề bài: Dùng ArrayBlockingQueue.take() để cấp phát an toàn
        Connection conn = connectionPool.take();

        // Kiểm tra xem connection có còn hợp lệ hay không
        if (conn == null || conn.isClosed() || !conn.isValid(2)) {
            logger.warn("Connection lấy từ pool không hợp lệ/đã đóng. Đang tạo connection mới thay thế...");
            if (conn != null && !conn.isClosed()) {
                try {
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
            conn = createNewConnection();
        }

        return conn;
    }

    /**
     * Trả Connection về Pool sau khi sử dụng xong.
     *
     * @param conn Connection cần trả về
     */
    public void releaseConnection(Connection conn) {
        if (conn == null) {
            return;
        }

        try {
            if (conn.isClosed() || !conn.isValid(2)) {
                logger.warn("Connection bị hỏng/đã đóng khi hoàn trả. Tạo connection mới thay thế vào pool...");
                Connection newConn = createNewConnection();
                connectionPool.offer(newConn);
            } else {
                // Đảm bảo không để dở dang transaction
                if (!conn.getAutoCommit()) {
                    conn.setAutoCommit(true);
                }
                boolean offered = connectionPool.offer(conn);
                if (!offered) {
                    logger.warn("Pool đã đầy, đóng connection trả thừa.");
                    conn.close();
                }
            }
        } catch (SQLException e) {
            logger.error("Lỗi khi giải phóng connection về pool", e);
            try {
                conn.close();
            } catch (SQLException ignored) {
            }
        }
    }

    /**
     * Đóng toàn bộ connection trong pool khi dừng ứng dụng.
     */
    public synchronized void closePool() {
        logger.info("Đang đóng toàn bộ connection trong Database Pool...");
        Connection conn;
        while ((conn = connectionPool.poll()) != null) {
            try {
                if (!conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                logger.error("Lỗi khi đóng connection trong pool", e);
            }
        }
        this.initialized = false;
        logger.info("Đã đóng hoàn toàn Connection Pool.");
    }

    /**
     * Lấy số lượng connection hiện có sẵn trong Pool.
     *
     * @return Số lượng connection rảnh
     */
    public int getAvailableConnections() {
        return connectionPool.size();
    }

    /**
     * Lấy tổng kích thước Pool được cấu hình.
     *
     * @return Kích thước pool
     */
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Kiểm tra trạng thái khởi tạo của Pool.
     *
     * @return true nếu pool đã sẵn sàng
     */
    public boolean isInitialized() {
        return initialized;
    }
}
