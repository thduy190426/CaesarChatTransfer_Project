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


public class TCPServer {

    private static final Logger logger = LoggerFactory.getLogger(TCPServer.class);

    private final DatabaseManager dbManager;
    private final int port;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private ThreadPoolExecutor threadPool;

    
    public TCPServer(DatabaseManager dbManager) {
        this(dbManager, Protocol.PORT);
    }

    
    public TCPServer(DatabaseManager dbManager, int port) {
        this.dbManager = dbManager;
        this.port = port;
    }

    
    public void start() {
        if (running) {
            logger.warn("Server is already running.");
            return;
        }

        
        threadPool = new ThreadPoolExecutor(
                ServerConfig.CORE_POOL_SIZE,
                ServerConfig.MAX_POOL_SIZE,
                ServerConfig.THREAD_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                new ThreadPoolExecutor.AbortPolicy()
        );

        try {
            
            serverSocket = new ServerSocket(port);
            running = true;

            logger.info("=== Caesar Chat Server đã khởi động ===");
            logger.info("Đang lắng nghe tại port: {}", serverSocket.getLocalPort());
            logger.info("Thread Pool: core={}, max={}", ServerConfig.CORE_POOL_SIZE, ServerConfig.MAX_POOL_SIZE);

            
            while (running) {
                Socket clientSocket = null;
                try {
                    clientSocket = serverSocket.accept();

                    
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
                    
                    if (running) {
                        logger.error("Lỗi khi accept connection: {}", e.getMessage());
                    }
                } catch (IOException e) {
                    
                    logger.error("Lỗi khi xử lý client mới: {}", e.getMessage());
                    if (clientSocket != null) {
                        try {
                            clientSocket.close();
                        } catch (IOException ignored) {}
                    }
                } catch (Exception e) {
                    logger.error("Lỗi không mong muốn trong vòng lặp accept: {}", e.getMessage(), e);
                    if (clientSocket != null) {
                        try {
                            clientSocket.close();
                        } catch (IOException ignored) {}
                    }
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

    
    public void shutdown() {
        logger.info("Đang shutdown server...");
        running = false;

        
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.error("Lỗi khi đóng ServerSocket: {}", e.getMessage());
            }
        }

        
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

    
    public int getPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : -1;
    }

    
    public int getActiveConnections() {
        return threadPool != null ? threadPool.getActiveCount() : 0;
    }

    
    public long getTaskCount() {
        return threadPool != null ? threadPool.getTaskCount() : 0;
    }
}
