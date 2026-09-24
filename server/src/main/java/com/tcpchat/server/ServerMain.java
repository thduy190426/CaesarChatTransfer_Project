package com.tcpchat.server;

import com.tcpchat.common.Protocol;
import com.tcpchat.server.config.ServerConfig;
import com.tcpchat.server.db.DatabaseManager;
import com.tcpchat.server.network.TCPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Entry point của Caesar Chat Server.
 * <p>Khởi tạo các dependencies theo đúng thứ tự và chạy TCP Server.</p>
 *
 * <p>Trình tự khởi tạo:
 * <ol>
 *   <li>Database Connection Pool</li>
 *   <li>Thư mục uploads</li>
 *   <li>TCPServer (blocking)</li>
 * </ol>
 * </p>
 *
 * <p>Shutdown Hook đảm bảo tài nguyên được giải phóng khi Ctrl+C.</p>
 */
public class ServerMain {

    private static final Logger logger = LoggerFactory.getLogger(ServerMain.class);

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("       CAESAR CHAT SERVER — Starting Up           ");
        System.out.println("==================================================");

        // 1. Khởi tạo Database
        logger.info("Đang khởi tạo Database Connection Pool...");
        DatabaseManager dbManager = DatabaseManager.getInstance();
        try {
            dbManager.initPool();
        } catch (Exception e) {
            logger.error("Không thể khởi tạo Database. Server dừng.", e);
            System.err.println("[FATAL] Không thể kết nối Database: " + e.getMessage());
            System.exit(1);
        }
        logger.info("Database Connection Pool sẵn sàng. Pool size: {}", dbManager.getPoolSize());

        // 2. Tạo thư mục uploads
        File uploadDir = new File(ServerConfig.UPLOAD_DIR);
        if (!uploadDir.exists()) {
            boolean created = uploadDir.mkdirs();
            if (created) {
                logger.info("Đã tạo thư mục uploads: {}", uploadDir.getAbsolutePath());
            } else {
                logger.warn("Không thể tạo thư mục uploads: {}", uploadDir.getAbsolutePath());
            }
        }

        // 3. Tạo TCPServer
        TCPServer server = new TCPServer(dbManager);

        // 4. Đăng ký Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered — đang dọn dẹp...");
            server.shutdown();
            dbManager.closePool();
            logger.info("Tất cả tài nguyên đã được giải phóng.");
        }, "shutdown-hook"));

        // 5. Start server (blocking)
        logger.info("Server sẵn sàng trên port {}. Đang chờ kết nối...", Protocol.PORT);
        server.start();
    }
}
