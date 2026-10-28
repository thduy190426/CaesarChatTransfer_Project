package com.tcpchat.server.transfer;

import com.tcpchat.common.MessageParser;
import com.tcpchat.common.Protocol;
import com.tcpchat.common.model.FilePacket;
import com.tcpchat.server.ClientHandlerThread;
import com.tcpchat.server.db.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class FileReceiverHandler {
    private static final Logger logger = LoggerFactory.getLogger(FileReceiverHandler.class);
    private static final String UPLOAD_DIR = "uploads";

    private final InputStream in;
    private final OutputStream out;
    private final String clientIp;
    private final FileRepository fileRepo;
    private final ClientHandlerThread parentHandler;

    public FileReceiverHandler(InputStream in, OutputStream out, String clientIp) {
        this(in, out, clientIp, null);
    }

    public FileReceiverHandler(InputStream in, OutputStream out, String clientIp, ClientHandlerThread parentHandler) {
        this.in = in;
        this.out = out;
        this.clientIp = clientIp;
        this.parentHandler = parentHandler;
        this.fileRepo = new FileRepository();
        
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public void receiveFile(String jsonHeader) {
        FilePacket header = MessageParser.parseFilePacket(jsonHeader);
        if (header == null || header.getFileSize() == null) {
            logger.error("Header file không hợp lệ từ {}", clientIp);
            return;
        }

        String fileName = header.getFileName();
        long fileSize = header.getFileSize();
        String mimeType = header.getMimeType();
        File saveFile = new File(UPLOAD_DIR, System.currentTimeMillis() + "_" + fileName);
        
        logger.info("Bắt đầu nhận file: {} ({} bytes)", fileName, fileSize);

        long recordId = fileRepo.saveFileTransfer(fileName, fileSize, mimeType, saveFile.getPath(), "IN_PROGRESS");

        try (FileOutputStream fos = new FileOutputStream(saveFile)) {
            byte[] buffer = new byte[Protocol.CHUNK_SIZE];
            long totalRead = 0;
            
            while (totalRead < fileSize) {
                int bytesToRead = (int) Math.min(buffer.length, fileSize - totalRead);
                int read = in.read(buffer, 0, bytesToRead);
                
                if (read == -1) {
                    throw new IOException("Client ngắt kết nối đột ngột khi đang gửi file.");
                }
                
                fos.write(buffer, 0, read);
                totalRead += read;

                if (parentHandler != null) {
                    parentHandler.updateLastActiveTime();
                }
            }
            
            fos.flush();
            logger.info("Đã nhận hoàn tất file: {}", saveFile.getPath());
            
            fileRepo.updateFileTransferStatus(recordId, "SUCCESS");
            
            FilePacket ack = new FilePacket(Protocol.TYPE_FILE_ACK);
            ack.setStatus("OK");
            ack.setSavedPath(saveFile.getPath());
            out.write((MessageParser.toJson(ack) + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();

        } catch (IOException e) {
            logger.error("Lỗi khi nhận file, tiến hành dọn dẹp file rác", e);
            if (saveFile.exists()) {
                saveFile.delete();
            }
            fileRepo.updateFileTransferStatus(recordId, "FAILED");
            
            FilePacket err = new FilePacket(Protocol.TYPE_ERROR);
            err.setStatus("Lỗi khi nhận file: " + e.getMessage());
            try {
                out.write((MessageParser.toJson(err) + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException ignored) {}
        }
    }
}
