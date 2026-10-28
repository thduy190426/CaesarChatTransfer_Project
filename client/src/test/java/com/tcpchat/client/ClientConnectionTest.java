package com.tcpchat.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ClientConnectionTest {

    private ServerSocket serverSocket;
    private Socket acceptedClientSocket;
    private ExecutorService executor;
    private ClientConnection clientConnection;

    @BeforeEach
    void setUp() throws Exception {
        serverSocket = new ServerSocket(0);
        executor = Executors.newSingleThreadExecutor();
        clientConnection = new ClientConnection();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (clientConnection != null) {
            clientConnection.close();
        }
        if (acceptedClientSocket != null && !acceptedClientSocket.isClosed()) {
            acceptedClientSocket.close();
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') buffer.write(b);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Kết nối, gửi KEY_EXCHANGE, TEXT, PONG và đóng kết nối sạch sẽ")
    void testConnectAndSendMessages() throws Exception {
        serverSocket.setSoTimeout(5000);
        int port = serverSocket.getLocalPort();

        Future<Socket> acceptFuture = executor.submit(() -> serverSocket.accept());

        clientConnection.connect("127.0.0.1", port, new MessageListener() {
            @Override public void onTextResultReceived(com.tcpchat.common.model.TextResult result) {}
            @Override public void onFileAckReceived(com.tcpchat.common.model.FilePacket ack) {}
            @Override public void onErrorReceived(com.tcpchat.common.model.Message error) {}
            @Override public void onDisconnected() {}
        });

        acceptedClientSocket = acceptFuture.get(3, TimeUnit.SECONDS);
        assertNotNull(acceptedClientSocket);
        acceptedClientSocket.setSoTimeout(3000);
        InputStream in = acceptedClientSocket.getInputStream();

        // 1. Kiểm tra sendKeyExchange
        clientConnection.sendKeyExchange(7);
        String keyLine = readLine(in);
        assertNotNull(keyLine);
        assertTrue(keyLine.contains("\"type\":\"KEY_EXCHANGE\""));
        assertTrue(keyLine.contains("\"key\":7"));

        // 2. Kiểm tra sendText
        clientConnection.sendText("Olssv");
        String textLine = readLine(in);
        assertNotNull(textLine);
        assertTrue(textLine.contains("\"type\":\"TEXT\""));
        assertTrue(textLine.contains("\"cipherText\":\"Olssv\""));
        assertTrue(textLine.contains("\"key\":7"));

        // 3. Kiểm tra sendPong
        clientConnection.sendPong();
        String pongLine = readLine(in);
        assertNotNull(pongLine);
        assertTrue(pongLine.contains("\"type\":\"PONG\""));

        // 4. Đóng kết nối
        clientConnection.close();
        acceptedClientSocket.close();
    }

    @Test
    @DisplayName("Khi client đang truyền file, sendPong() không ghi PONG vào socket để tránh làm hỏng stream nhị phân")
    void testSendPongSkippedDuringFileTransfer() throws Exception {
        serverSocket.setSoTimeout(5000);
        int port = serverSocket.getLocalPort();

        Future<Socket> acceptFuture = executor.submit(() -> serverSocket.accept());

        clientConnection.connect("127.0.0.1", port, new MessageListener() {
            @Override public void onTextResultReceived(com.tcpchat.common.model.TextResult result) {}
            @Override public void onFileAckReceived(com.tcpchat.common.model.FilePacket ack) {}
            @Override public void onErrorReceived(com.tcpchat.common.model.Message error) {}
            @Override public void onDisconnected() {}
        });

        acceptedClientSocket = acceptFuture.get(3, TimeUnit.SECONDS);
        assertNotNull(acceptedClientSocket);
        acceptedClientSocket.setSoTimeout(3000);
        InputStream in = acceptedClientSocket.getInputStream();

        File tempFile = File.createTempFile("test_skip_pong", ".dat");
        tempFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(new byte[1024]);
        }

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);

        new Thread(() -> {
            try {
                clientConnection.sendFile(tempFile, percent -> {
                    started.countDown();
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                });
            } catch (Exception ignored) {
            } finally {
                finished.countDown();
            }
        }).start();

        started.await(2, TimeUnit.SECONDS);
        assertTrue(clientConnection.isSendingFile(), "Phải ở trạng thái isSendingFile");

        // Gọi sendPong trong khi file đang được truyền
        clientConnection.sendPong();

        finished.await(3, TimeUnit.SECONDS);
        assertFalse(clientConnection.isSendingFile(), "Phải kết thúc trạng thái isSendingFile");

        String headerLine = readLine(in);
        assertNotNull(headerLine);
        assertTrue(headerLine.contains("\"type\":\"FILE\""));

        byte[] buffer = new byte[1024];
        int totalRead = 0;
        while (totalRead < 1024) {
            int read = in.read(buffer, totalRead, 1024 - totalRead);
            if (read == -1) break;
            totalRead += read;
        }
        assertEquals(1024, totalRead);

        // Đảm bảo không có dòng PONG nào bị ghi vào stream
        Thread.sleep(100);
        assertEquals(0, in.available(), "Không được có gói tin PONG nào được gửi chèn vào stream");

        clientConnection.close();
        acceptedClientSocket.close();
    }
}
