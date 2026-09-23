package com.tcpchat.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class TCPServer {
    private static final Logger logger = LoggerFactory.getLogger(TCPServer.class);
    private final int port;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private boolean isRunning;
    private List<ClientHandlerThread> activeClients;

    public TCPServer(int port) {
        this.port = port;
        // Khởi tạo ThreadPool với tối đa 50 threads
        this.threadPool = Executors.newFixedThreadPool(50);
        this.activeClients = new CopyOnWriteArrayList<>();
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            logger.info("TCP Server đang lắng nghe trên cổng: {}", port);

            // Bắt đầu luồng Heartbeat (Ping định kỳ)
            startHeartbeatThread();

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    logger.info("Có client mới kết nối từ IP: {}", clientSocket.getInetAddress().getHostAddress());
                    
                    ClientHandlerThread clientHandler = new ClientHandlerThread(clientSocket, this);
                    activeClients.add(clientHandler);
                    threadPool.execute(clientHandler);
                } catch (IOException e) {
                    if (isRunning) {
                        logger.error("Lỗi khi chấp nhận kết nối từ client", e);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Không thể khởi động Server trên cổng {}", port, e);
        } finally {
            stop();
        }
    }

    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (threadPool != null && !threadPool.isShutdown()) {
                threadPool.shutdown();
                try {
                    if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                        threadPool.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    threadPool.shutdownNow();
                }
            }
            logger.info("Đã tắt TCP Server.");
        } catch (IOException e) {
            logger.error("Lỗi khi đóng ServerSocket", e);
        }
    }

    private void startHeartbeatThread() {
        Thread heartbeatThread = new Thread(() -> {
            while (isRunning) {
                try {
                    Thread.sleep(10000); // 10 giây ping 1 lần
                    for (ClientHandlerThread client : activeClients) {
                        client.sendPing();
                        client.checkAlive();
                    }
                } catch (InterruptedException e) {
                    logger.warn("Luồng Heartbeat bị gián đoạn", e);
                    break;
                }
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    public void removeClient(ClientHandlerThread client) {
        activeClients.remove(client);
    }
}
