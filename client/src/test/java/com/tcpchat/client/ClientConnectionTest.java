package com.tcpchat.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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
        BufferedReader serverReader = new BufferedReader(
                new InputStreamReader(acceptedClientSocket.getInputStream(), StandardCharsets.UTF_8));

        // 1. Kiểm tra sendKeyExchange
        clientConnection.sendKeyExchange(7);
        String keyLine = serverReader.readLine();
        assertNotNull(keyLine);
        assertTrue(keyLine.contains("\"type\":\"KEY_EXCHANGE\""));
        assertTrue(keyLine.contains("\"key\":7"));

        // 2. Kiểm tra sendText
        clientConnection.sendText("Olssv");
        String textLine = serverReader.readLine();
        assertNotNull(textLine);
        assertTrue(textLine.contains("\"type\":\"TEXT\""));
        assertTrue(textLine.contains("\"cipherText\":\"Olssv\""));
        assertTrue(textLine.contains("\"key\":7"));

        // 3. Kiểm tra sendPong
        clientConnection.sendPong();
        String pongLine = serverReader.readLine();
        assertNotNull(pongLine);
        assertTrue(pongLine.contains("\"type\":\"PONG\""));

        // 4. Đóng kết nối
        clientConnection.close();
        acceptedClientSocket.close();
    }
}
