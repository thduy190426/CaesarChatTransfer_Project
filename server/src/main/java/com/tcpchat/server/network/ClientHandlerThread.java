package com.tcpchat.server.network;

import com.tcpchat.common.Protocol;
import com.tcpchat.common.analysis.CharFrequencyAnalyzer;
import com.tcpchat.common.crypto.CaesarCipher;
import com.tcpchat.common.model.FilePacket;
import com.tcpchat.common.model.Message;
import com.tcpchat.common.model.TextResult;
import com.tcpchat.server.config.ServerConfig;
import com.tcpchat.server.db.ClientRepository;
import com.tcpchat.server.db.DatabaseManager;
import com.tcpchat.server.db.FileRepository;
import com.tcpchat.server.db.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Xử lý toàn bộ giao tiếp với 1 Client kết nối.
 * <p>Mỗi instance chạy trên 1 thread riêng từ ThreadPoolExecutor.
 * Implements {@link HeartbeatTask.HeartbeatTarget} để HeartbeatTask
 * có thể gửi PING và kiểm tra timeout.</p>
 *
 * <p><b>Anti-domino:</b> Mọi lỗi ở level message (parse, validate, business)
 * đều bị bắt và xử lý tại chỗ, KHÔNG lan ra ngoài thread này.</p>
 */
public class ClientHandlerThread implements Runnable, HeartbeatTask.HeartbeatTarget {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandlerThread.class);

    // === Socket & I/O ===
    private final Socket socket;
    private final BufferedReader reader;
    private final PrintWriter writer;
    private final DataInputStream dataIn;
    private final String clientId; // "ip:port" cho logging

    // === State ===
    private volatile boolean connected = true;
    private volatile long lastPongTime;
    private Integer caesarKey = null; // null = chưa KEY_EXCHANGE

    // === Dependencies ===
    private final MessageRepository messageRepo;
    private final FileRepository fileRepo;
    private final ClientRepository clientRepo;

    // === Heartbeat ===
    private ScheduledExecutorService heartbeatScheduler;

    /**
     * Khởi tạo handler cho 1 client socket.
     *
     * @param socket    Socket kết nối từ ServerSocket.accept()
     * @param dbManager Database Manager để tạo repositories
     * @throws IOException nếu không thể tạo streams
     */
    public ClientHandlerThread(Socket socket, DatabaseManager dbManager) throws IOException {
        this.socket = socket;
        this.clientId = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        this.lastPongTime = System.currentTimeMillis();

        // Tạo streams — thứ tự quan trọng: output trước input để tránh deadlock
        this.writer = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        this.reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.dataIn = new DataInputStream(socket.getInputStream());

        // Tạo repositories
        this.messageRepo = new MessageRepository(dbManager);
        this.fileRepo = new FileRepository(dbManager);
        this.clientRepo = new ClientRepository(dbManager);
    }

    @Override
    public void run() {
        logger.info("Client {} đã kết nối", clientId);

        try {
            // 1. Ghi client vào DB
            String ip = socket.getInetAddress().getHostAddress();
            int port = socket.getPort();
            clientRepo.saveOrUpdateClient(ip, port, "CONNECTED");

            // 2. Khởi tạo Heartbeat
            startHeartbeat();

            // 3. Vòng lặp đọc message
            String jsonLine;
            while (connected && (jsonLine = reader.readLine()) != null) {
                if (jsonLine.isBlank()) {
                    continue; // Bỏ qua dòng trống
                }
                processMessage(jsonLine);
            }

        } catch (SocketException e) {
            // Client ngắt kết nối đột ngột — bình thường
            logger.info("Client {} ngắt kết nối: {}", clientId, e.getMessage());
        } catch (IOException e) {
            logger.warn("Lỗi I/O với client {}: {}", clientId, e.getMessage());
        } catch (Exception e) {
            logger.error("Lỗi không mong đợi với client {}", clientId, e);
        } finally {
            cleanup();
        }
    }

    /**
     * Phân loại và xử lý 1 JSON message.
     * <p>Mọi exception ở đây đều bị bắt — KHÔNG lan ra vòng lặp chính.</p>
     */
    private void processMessage(String jsonLine) {
        try {
            String type = MessageParser.detectType(jsonLine);

            switch (type) {
                case Protocol.TYPE_KEY_EXCHANGE:
                    handleKeyExchange(jsonLine);
                    break;
                case Protocol.TYPE_TEXT:
                    handleTextMessage(jsonLine);
                    break;
                case Protocol.TYPE_FILE:
                    handleFileTransfer(jsonLine);
                    break;
                case Protocol.TYPE_PONG:
                    handlePong();
                    break;
                default:
                    logger.warn("Client {} gửi loại message không hỗ trợ: {}", clientId, type);
                    break;
            }
        } catch (IllegalArgumentException e) {
            // JSON hỏng hoặc thiếu type
            logger.warn("Client {} gửi message không hợp lệ: {}", clientId, e.getMessage());
            sendError("Invalid message format: " + e.getMessage());
        } catch (Exception e) {
            // Bất kỳ lỗi nào khác — bắt để vòng lặp tiếp tục
            logger.error("Lỗi khi xử lý message từ client {}", clientId, e);
            sendError("Server error: " + e.getMessage());
        }
    }

    // ========== Message Handlers ==========

    private void handleKeyExchange(String jsonLine) {
        Message msg = MessageParser.parseMessage(jsonLine);
        Integer key = msg.getKey();

        if (key == null || key < 1 || key > 25) {
            sendError("Caesar key phải từ 1 đến 25, nhận được: " + key);
            return;
        }

        this.caesarKey = key;
        logger.info("Client {} đã exchange key = {}", clientId, key);
    }

    private void handleTextMessage(String jsonLine) {
        // 1. Kiểm tra key đã exchange chưa
        if (caesarKey == null) {
            sendError("Chưa thực hiện KEY_EXCHANGE. Hãy gửi KEY_EXCHANGE trước khi gửi TEXT.");
            return;
        }

        // 2. Parse message
        Message msg = MessageParser.parseMessage(jsonLine);
        String cipherText = msg.getCipherText();

        if (cipherText == null || cipherText.isEmpty()) {
            sendError("cipherText không được rỗng");
            return;
        }

        // 3. Decrypt
        String plainText = CaesarCipher.decrypt(cipherText, caesarKey);

        // 4. Phân tích tần suất
        Map<Character, Integer> freqMap = CharFrequencyAnalyzer.analyzeLettersOnly(plainText);

        // 5. Lưu DB
        String clientIp = socket.getInetAddress().getHostAddress();
        long msgId = messageRepo.saveMessage(clientIp, cipherText, caesarKey, plainText, freqMap);
        if (msgId < 0) {
            logger.warn("Không thể lưu tin nhắn vào DB cho client {}", clientId);
            // Vẫn gửi kết quả về client — DB fail không block user
        }

        // 6. Gửi TextResult
        TextResult result = new TextResult(plainText, freqMap);
        sendMessage(MessageParser.toJson(result));

        logger.info("Client {} — TEXT xử lý thành công: cipher='{}' → plain='{}'",
                clientId, cipherText, plainText);
    }

    private void handleFileTransfer(String jsonLine) {
        FilePacket header = MessageParser.parseFilePacket(jsonLine);

        // 1. Validate
        String fileName = header.getFileName();
        Long fileSize = header.getFileSize();

        if (fileName == null || fileName.isBlank()) {
            sendError("fileName không được rỗng");
            return;
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            sendError("fileName không hợp lệ (không được chứa path separators hoặc '..')");
            return;
        }
        if (fileSize == null || fileSize <= 0) {
            sendError("fileSize phải > 0");
            return;
        }
        if (fileSize > ServerConfig.MAX_FILE_SIZE) {
            sendError("File quá lớn. Tối đa " + (ServerConfig.MAX_FILE_SIZE / 1024 / 1024) + "MB");
            return;
        }

        // 2. Sanitize file name
        String sanitized = sanitizeFileName(fileName);
        String tempName = System.currentTimeMillis() + "_" + sanitized + ".tmp";
        Path uploadDir = Path.of(ServerConfig.UPLOAD_DIR);
        Path tempFile = uploadDir.resolve(tempName);
        Path finalFile = uploadDir.resolve(sanitized);

        try {
            // Tạo thư mục nếu chưa có
            Files.createDirectories(uploadDir);

            // 3. Đọc binary chunks → ghi vào file tạm
            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                long remaining = fileSize;
                byte[] buffer = new byte[Protocol.CHUNK_SIZE];

                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    dataIn.readFully(buffer, 0, toRead);
                    fos.write(buffer, 0, toRead);
                    remaining -= toRead;
                }
                fos.flush();
            }

            // 4. Rename tạm → chính thức
            Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING);

            // 5. Ghi DB
            fileRepo.saveFileTransfer(
                    fileName, fileSize,
                    header.getMimeType(),
                    finalFile.toString(),
                    "SUCCESS"
            );

            // 6. Gửi FILE_ACK
            FilePacket ack = new FilePacket(Protocol.TYPE_FILE_ACK);
            ack.setFileName(fileName);
            ack.setStatus("SUCCESS");
            ack.setSavedPath(finalFile.toString());
            sendMessage(MessageParser.toJson(ack));

            logger.info("Client {} — FILE nhận thành công: '{}' ({} bytes)",
                    clientId, fileName, fileSize);

        } catch (IOException e) {
            // Transfer thất bại — dọn dẹp file tạm
            logger.error("Lỗi khi nhận file '{}' từ client {}: {}",
                    fileName, clientId, e.getMessage());

            // Xóa file tạm
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException deleteErr) {
                logger.error("Không thể xóa file tạm: {}", tempFile, deleteErr);
            }

            // Ghi trạng thái FAILED vào DB
            fileRepo.saveFileTransfer(fileName, fileSize,
                    header.getMimeType(), null, "FAILED");

            sendError("File transfer failed: " + e.getMessage());
        }
    }

    private void handlePong() {
        this.lastPongTime = System.currentTimeMillis();
        // Log level TRACE — không spam log
        logger.trace("Client {} PONG received", clientId);
    }

    // ========== Utility Methods ==========

    /**
     * Gửi JSON message qua socket. Thread-safe (synchronized).
     * <p>Được gọi từ cả thread chính (run) và HeartbeatTask thread.</p>
     */
    @Override
    public synchronized void sendMessage(String jsonLine) {
        if (!connected || writer.checkError()) {
            return;
        }
        writer.println(jsonLine);
        writer.flush();
    }

    /**
     * Gửi ERROR message về client.
     */
    private void sendError(String errorMessage) {
        Message error = new Message(Protocol.TYPE_ERROR);
        error.setMessage(errorMessage);
        sendMessage(MessageParser.toJson(error));
    }

    /**
     * Ngắt kết nối client.
     */
    @Override
    public void disconnect() {
        this.connected = false;
        try {
            socket.close(); // Sẽ làm readLine() throw SocketException → thoát vòng lặp
        } catch (IOException e) {
            logger.debug("Lỗi khi đóng socket cho client {}: {}", clientId, e.getMessage());
        }
    }

    public boolean isConnected() {
        return connected;
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public long getLastPongTime() {
        return lastPongTime;
    }

    /**
     * Sanitize file name: chỉ giữ lại [a-zA-Z0-9._-], thay thế phần còn lại bằng '_'.
     */
    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Khởi tạo ScheduledExecutorService cho HeartbeatTask.
     */
    private void startHeartbeat() {
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-" + clientId);
            t.setDaemon(true);
            return t;
        });

        HeartbeatTask task = new HeartbeatTask(this);
        heartbeatScheduler.scheduleAtFixedRate(
                task,
                ServerConfig.HEARTBEAT_INITIAL_DELAY_MS,
                ServerConfig.HEARTBEAT_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * Dọn dẹp toàn bộ tài nguyên khi client ngắt kết nối.
     */
    private void cleanup() {
        connected = false;

        // 1. Dừng heartbeat
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdownNow();
        }

        // 2. Cập nhật DB
        String ip = socket.getInetAddress().getHostAddress();
        clientRepo.updateClientStatus(ip, "DISCONNECTED");

        // 3. Đóng streams
        closeQuietly(reader);
        closeQuietly(dataIn);
        // writer đóng tự động khi socket đóng

        // 4. Đóng socket
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            logger.debug("Lỗi khi đóng socket {}: {}", clientId, e.getMessage());
        }

        logger.info("Client {} đã ngắt kết nối, tài nguyên đã được dọn dẹp", clientId);
    }

    private void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception ignored) {
            }
        }
    }
}
