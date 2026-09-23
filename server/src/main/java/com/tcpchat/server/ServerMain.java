package com.tcpchat.server;

import com.tcpchat.common.Protocol;
import com.tcpchat.server.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerMain {
    private static final Logger logger = LoggerFactory.getLogger(ServerMain.class);

    public static void main(String[] args) {
        logger.info("Khởi động Caesar Chat Server...");

        try {
            // Khởi tạo Database Pool
            DatabaseManager dbManager = DatabaseManager.getInstance();
            dbManager.initPool();

            // Khởi động TCP Server
            int port = Protocol.PORT;
            if (args.length > 0) {
                port = Integer.parseInt(args[0]);
            }
            
            TCPServer server = new TCPServer(port);
            server.start();

            // Thêm Shutdown Hook để dọn dẹp khi tắt server
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Đang tắt server...");
                server.stop();
                dbManager.closePool();
            }));

        } catch (Exception e) {
            logger.error("Lỗi khi khởi động Server: ", e);
            System.exit(1);
        }
    }
}
