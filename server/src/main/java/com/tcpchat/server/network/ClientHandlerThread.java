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


public class ClientHandlerThread implements Runnable, HeartbeatTask.HeartbeatTarget {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandlerThread.class);

    
    private final Socket socket;
    private final PrintWriter writer;
    private final DataInputStream dataIn;
    private final String clientId; 

    
    private volatile boolean connected = true;
    private volatile boolean isReceivingFile = false;
    private volatile long lastPongTime;
    private Integer caesarKey = null; 

    
    private final MessageRepository messageRepo;
    private final FileRepository fileRepo;
    private final ClientRepository clientRepo;

    
    private ScheduledExecutorService heartbeatScheduler;

    
    public ClientHandlerThread(Socket socket, DatabaseManager dbManager) throws IOException {
        this.socket = socket;
        this.clientId = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        this.lastPongTime = System.currentTimeMillis();

        
        this.writer = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        BufferedInputStream bufferedStream = new BufferedInputStream(socket.getInputStream());
        this.dataIn = new DataInputStream(bufferedStream);

        
        this.messageRepo = new MessageRepository(dbManager);
        this.fileRepo = new FileRepository(dbManager);
        this.clientRepo = new ClientRepository(dbManager);
    }

    @Override
    public void run() {
        logger.info("Client {} đã kết nối", clientId);

        try {
            
            String ip = socket.getInetAddress().getHostAddress();
            int port = socket.getPort();
            clientRepo.saveOrUpdateClient(ip, port, "CONNECTED");

            
            startHeartbeat();

            
            String jsonLine;
            while (connected && (jsonLine = readLine(dataIn)) != null) {
                if (jsonLine.isBlank()) {
                    continue; 
                }
                processMessage(jsonLine);
            }

        } catch (SocketException e) {
            
            logger.info("Client {} ngắt kết nối: {}", clientId, e.getMessage());
        } catch (IOException e) {
            logger.warn("Lỗi I/O với client {}: {}", clientId, e.getMessage());
        } catch (Exception e) {
            logger.error("Lỗi không mong đợi với client {}", clientId, e);
        } finally {
            cleanup();
        }
    }

    
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
            
            logger.warn("Client {} gửi message không hợp lệ: {}", clientId, e.getMessage());
            sendError("Invalid message format: " + e.getMessage());
        } catch (Exception e) {
            
            logger.error("Lỗi khi xử lý message từ client {}", clientId, e);
            sendError("Server error: " + e.getMessage());
        }
    }

    

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
        
        if (caesarKey == null) {
            sendError("Chưa thực hiện KEY_EXCHANGE. Hãy gửi KEY_EXCHANGE trước khi gửi TEXT.");
            return;
        }

        
        Message msg = MessageParser.parseMessage(jsonLine);
        String cipherText = msg.getCipherText();

        if (cipherText == null || cipherText.isEmpty()) {
            sendError("cipherText không được rỗng");
            return;
        }

        
        String plainText = CaesarCipher.decrypt(cipherText, caesarKey);

        
        Map<Character, Integer> freqMap = CharFrequencyAnalyzer.analyzeLettersOnly(plainText);

        
        String clientIp = socket.getInetAddress().getHostAddress();
        long msgId = messageRepo.saveMessage(clientIp, cipherText, caesarKey, plainText, freqMap);
        if (msgId < 0) {
            logger.warn("Không thể lưu tin nhắn vào DB cho client {}", clientId);
            
        }

        
        TextResult result = new TextResult(plainText, freqMap);
        sendMessage(MessageParser.toJson(result));

        logger.info("Client {} — TEXT xử lý thành công: cipher='{}' → plain='{}'",
                clientId, cipherText, plainText);
    }

    private void handleFileTransfer(String jsonLine) {
        FilePacket header = MessageParser.parseFilePacket(jsonLine);

        
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

        
        String sanitized = sanitizeFileName(fileName);
        String tempName = System.currentTimeMillis() + "_" + sanitized + ".tmp";
        Path uploadDir = Path.of(ServerConfig.UPLOAD_DIR);
        Path tempFile = uploadDir.resolve(tempName);
        Path finalFile = uploadDir.resolve(sanitized);

        this.isReceivingFile = true;
        try {
            Files.createDirectories(uploadDir);

            
            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                long remaining = fileSize;
                byte[] buffer = new byte[Protocol.CHUNK_SIZE];

                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    dataIn.readFully(buffer, 0, toRead);
                    fos.write(buffer, 0, toRead);
                    this.lastPongTime = System.currentTimeMillis();
                    remaining -= toRead;
                }
                fos.flush();
            }

            
            Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING);

            
            fileRepo.saveFileTransfer(
                    fileName, fileSize,
                    header.getMimeType(),
                    finalFile.toString(),
                    "SUCCESS"
            );

            
            FilePacket ack = new FilePacket(Protocol.TYPE_FILE_ACK);
            ack.setFileName(fileName);
            ack.setStatus("SUCCESS");
            ack.setSavedPath(finalFile.toString());
            sendMessage(MessageParser.toJson(ack));

            logger.info("Client {} — FILE nhận thành công: '{}' ({} bytes)",
                    clientId, fileName, fileSize);

        } catch (IOException e) {
            
            logger.error("Lỗi khi nhận file '{}' từ client {}: {}",
                    fileName, clientId, e.getMessage());

            
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException deleteErr) {
                logger.error("Không thể xóa file tạm: {}", tempFile, deleteErr);
            }

            
            fileRepo.saveFileTransfer(fileName, fileSize,
                    header.getMimeType(), null, "FAILED");

            sendError("File transfer failed: " + e.getMessage());
        } finally {
            this.isReceivingFile = false;
            this.lastPongTime = System.currentTimeMillis();
        }
    }

    private void handlePong() {
        this.lastPongTime = System.currentTimeMillis();
        
        logger.trace("Client {} PONG received", clientId);
    }

    private String readLine(DataInputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        boolean hasData = false;
        try {
            while (true) {
                byte b = in.readByte();
                hasData = true;
                if (b == '\n') {
                    break;
                }
                if (b != '\r') {
                    buffer.write(b);
                }
            }
        } catch (EOFException e) {
            if (!hasData) return null;
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    

    
    @Override
    public synchronized void sendMessage(String jsonLine) {
        if (!connected || writer.checkError()) {
            return;
        }
        if (isReceivingFile && jsonLine.contains("\"type\":\"PING\"")) {
            return;
        }
        writer.println(jsonLine);
        writer.flush();
    }

    
    private void sendError(String errorMessage) {
        Message error = new Message(Protocol.TYPE_ERROR);
        error.setMessage(errorMessage);
        sendMessage(MessageParser.toJson(error));
    }

    
    @Override
    public void disconnect() {
        this.connected = false;
        try {
            socket.close(); 
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

    
    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    
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

    
    private void cleanup() {
        connected = false;

        
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdownNow();
        }

        
        String ip = socket.getInetAddress().getHostAddress();
        clientRepo.updateClientStatus(ip, "DISCONNECTED");

        
        closeQuietly(dataIn);
        

        
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
