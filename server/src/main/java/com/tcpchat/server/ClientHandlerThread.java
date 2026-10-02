package com.tcpchat.server;

import com.tcpchat.common.MessageParser;
import com.tcpchat.common.Protocol;
import com.tcpchat.common.analysis.CharFrequencyAnalyzer;
import com.tcpchat.common.crypto.CaesarCipher;
import com.tcpchat.common.model.Message;
import com.tcpchat.common.model.TextResult;
import com.tcpchat.server.db.ClientRepository;
import com.tcpchat.server.db.MessageRepository;
import com.tcpchat.server.transfer.FileReceiverHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ClientHandlerThread implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandlerThread.class);

    private final Socket clientSocket;
    private final TCPServer server;
    private InputStream in;
    private OutputStream out;
    private volatile boolean isRunning = true;
    private long lastPongTime;
    
    private final MessageRepository messageRepo;
    private final ClientRepository clientRepo;

    public ClientHandlerThread(Socket socket, TCPServer server) {
        this.clientSocket = socket;
        this.server = server;
        this.lastPongTime = System.currentTimeMillis();
        this.messageRepo = new MessageRepository();
        this.clientRepo = new ClientRepository();
    }

    @Override
    public void run() {
        String clientIp = clientSocket.getInetAddress().getHostAddress();
        int port = clientSocket.getPort();
        
        try {
            in = clientSocket.getInputStream();
            out = clientSocket.getOutputStream();

            clientRepo.saveOrUpdateClient(clientIp, port, "CONNECTED");

            while (isRunning) {
                String line = readLineSafe(in);
                if (line == null) {
                    break; // Ngắt kết nối
                }

                if (line.trim().isEmpty()) continue;

                Message msg = MessageParser.parseMessage(line);
                if (msg == null || msg.getType() == null) continue;

                switch (msg.getType()) {
                    case Protocol.TYPE_KEY_EXCHANGE:
                        logger.info("Nhận KEY_EXCHANGE từ {}: key={}", clientIp, msg.getKey());
                        break;

                    case Protocol.TYPE_TEXT:
                        handleTextMessage(clientIp, msg);
                        break;

                    case Protocol.TYPE_FILE:
                        logger.info("Nhận yêu cầu chuyển file từ {}", clientIp);
                        FileReceiverHandler fileHandler = new FileReceiverHandler(in, out, clientIp);
                        fileHandler.receiveFile(line); // Pass JSON header to handler
                        break;

                    case Protocol.TYPE_PONG:
                        lastPongTime = System.currentTimeMillis();
                        break;

                    default:
                        logger.warn("Không rõ loại tin nhắn: {}", msg.getType());
                        break;
                }
            }
        } catch (IOException e) {
            logger.warn("Lỗi đọc dữ liệu từ client {}: {}", clientIp, e.getMessage());
        } finally {
            cleanup(clientIp);
        }
    }

    private void handleTextMessage(String clientIp, Message msg) {
        try {
            if (msg.getKey() == null) {
                sendError("Thiếu khóa Caesar (key)");
                return;
            }

            // Giải mã
            String plainText = CaesarCipher.decrypt(msg.getCipherText(), msg.getKey());
            
            // Phân tích tần suất
            Map<Character, Integer> freq = CharFrequencyAnalyzer.analyze(plainText);
            
            // Lưu vào CSDL
            messageRepo.saveMessage(clientIp, msg.getCipherText(), msg.getKey(), plainText, freq);

            // Gửi kết quả về client
            TextResult result = new TextResult(plainText, freq);
            sendMessage(MessageParser.toJson(result));

        } catch (Exception e) {
            logger.error("Lỗi khi xử lý tin nhắn TEXT", e);
            sendError("Lỗi server: " + e.getMessage());
        }
    }

    public void sendPing() {
        Message ping = new Message(Protocol.TYPE_PING);
        sendMessage(MessageParser.toJson(ping));
    }

    public void checkAlive() {
        // Nếu sau 30 giây không nhận được PONG thì ngắt kết nối
        if (System.currentTimeMillis() - lastPongTime > 30000) {
            logger.warn("Client {} không phản hồi Heartbeat. Đang ngắt kết nối...", clientSocket.getInetAddress().getHostAddress());
            cleanup(clientSocket.getInetAddress().getHostAddress());
        }
    }

    private void sendMessage(String json) {
        try {
            if (isRunning && !clientSocket.isClosed()) {
                out.write((json + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (IOException e) {
            logger.error("Lỗi khi gửi dữ liệu cho client", e);
            cleanup(clientSocket.getInetAddress().getHostAddress());
        }
    }

    private void sendError(String errorMsg) {
        Message err = new Message(Protocol.TYPE_ERROR);
        err.setMessage(errorMsg);
        sendMessage(MessageParser.toJson(err));
    }

    private void cleanup(String clientIp) {
        isRunning = false;
        server.removeClient(this);
        clientRepo.updateClientStatus(clientIp, "DISCONNECTED");
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            logger.error("Lỗi khi đóng socket client", e);
        }
        logger.info("Đã ngắt kết nối với client {}", clientIp);
    }

    /**
     * Đọc từng byte cho đến khi gặp \n. Không dùng BufferedReader để tránh việc đọc lố (buffer over-read) vào dữ liệu binary của File.
     */
    private String readLineSafe(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buffer.write(b);
            }
        }
        if (buffer.size() == 0 && b == -1) {
            return null; // EOF
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
