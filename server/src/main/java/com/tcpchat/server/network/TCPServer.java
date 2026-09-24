package com.tcpchat.server.network;

import com.tcpchat.common.Protocol;
import com.tcpchat.server.config.ServerConfig;
import com.tcpchat.server.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.*;

/**
 * TCP Server chính — quản lý vòng đời: accept connections → dispatch to handlers → shutdown.
 *
 * <p>Sử dụng {@link ThreadPoolExecutor} được cấu hình tường minh (bounded queue,
 * CallerRunsPolicy) thay vì {@code Executors.newFixedThreadPool()} để tránh
 * unbounded queue gây OOM khi quá tải.</p>
 *
 * <p><b>Anti-domino:</b> Lỗi khi accept() hoặc tạo handler cho 1 client
 * được bắt riêng — server tiếp tục phục vụ client khác.</p>
 */
public class TCPServer {

    private static final Logger logger = LoggerFactory.getLogger(TCPServer.class);

    private final DatabaseManager dbManager;
    private final int port;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private ThreadPoolExecutor threadPool;

    /**
     * Tạo TCPServer với port mặc định từ Protocol.PORT.
     */
    public TCPServer(DatabaseManager dbManager) {
        this(dbManager, Protocol.PORT);
    }

    /**
     * Tạo TCPServer với port tùy chỉnh (dùng cho testing).
     *
     * @param dbManager Database Manager
     * @param port      Port lắng nghe (0 = OS tự chọn)
     */
    public TCPServer(DatabaseManager dbManager, int port) {
        this.dbManager = dbManager;
        this.port = port;
    }

    /**
     * Khởi tạo thread pool và bắt đầu accept loop. Blocking.
     */
    public void start() {
        if (running) {
            logger.warn("Server is already running.");
            return;
        }

        // 1. Tạo ThreadPoolExecutor tường minh
        threadPool = new ThreadPoolExecutor(
                ServerConfig.CORE_POOL_SIZE,
                ServerConfig.MAX_POOL_SIZE,
                ServerConfig.THREAD_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                new ThreadPoolExecutor.AbortPolicy()
        );

        try {
            // 2. Bind port
            serverSocket = new ServerSocket(port);
            running = true;

            logger.info("=== Caesar Chat Server đã khởi động ===");
            logger.info("Đang lắng nghe tại port: {}", serverSocket.getLocalPort());
            logger.info("Thread Pool: core={}, max={}", ServerConfig.CORE_POOL_SIZE, ServerConfig.MAX_POOL_SIZE);

            // 3. Accept loop
            while (running) {
                Socket clientSocket = null;
                try {
                    clientSocket = serverSocket.accept();

                    // Tạo handler cho client mới
                    ClientHandlerThread handler = new ClientHandlerThread(clientSocket, dbManager);
                    threadPool.submit(handler);

                } catch (RejectedExecutionException e) {
                    logger.warn("Server overload, rejecting connection");
                    if (clientSocket != null) {
                        try {
                            clientSocket.close();
                        } catch (IOException ignored) {}
                    }
                } catch (SocketException e) {
                    // ServerSocket bị close trong shutdown() → bình thường
                    if (running) {
                        logger.error("Lỗi khi accept connection: {}", e.getMessage());
                    }
                } catch (IOException e) {
                    // Lỗi tạo handler cho 1 client — KHÔNG crash server
                    logger.error("Lỗi khi xử lý client mới: {}", e.getMessage());
                } catch (Exception e) {
                    logger.error("Lỗi không mong muốn trong vòng lặp accept: {}", e.getMessage(), e);
                }
            }

        } catch (IOException e) {
            if (threadPool != null) {
                threadPool.shutdownNow();
            }
            logger.error("Không thể bind port {}: {}", port, e.getMessage());
            throw new RuntimeException("Server không thể khởi động", e);
        } finally {
            running = false;
        }
    }

    /**
     * Graceful shutdown: đóng ServerSocket, chờ threads hoàn tất.
     */
    public void shutdown() {
        logger.info("Đang shutdown server...");
        running = false;

        // 1. Đóng ServerSocket → accept() throw SocketException → thoát loop
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.error("Lỗi khi đóng ServerSocket: {}", e.getMessage());
            }
        }

        // 2. Shutdown thread pool
        if (threadPool != null) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(ServerConfig.SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    logger.warn("Thread pool chưa dừng hết sau {}s, forcing shutdown...",
                            ServerConfig.SHUTDOWN_TIMEOUT_SECONDS);
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("Server đã shutdown hoàn tất.");
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Lấy port thực tế đang lắng nghe (hữu ích khi dùng port=0).
     */
    public int getPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : -1;
    }

    /**
     * Get the number of active connections.
     */
    public int getActiveConnections() {
        return threadPool != null ? threadPool.getActiveCount() : 0;
    }
}
