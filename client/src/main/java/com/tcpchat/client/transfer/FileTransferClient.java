package com.tcpchat.client.transfer;

import com.tcpchat.client.ProgressListener;
import com.tcpchat.common.MessageParser;
import com.tcpchat.common.Protocol;
import com.tcpchat.common.model.FilePacket;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class FileTransferClient {
    private final OutputStream out;

    public FileTransferClient(OutputStream out) {
        this.out = out;
    }

    public synchronized void sendFile(File file, ProgressListener listener) throws IOException {
        long fileSize = file.length();
        String mimeType = Files.probeContentType(file.toPath());
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }

        // Gửi Header
        FilePacket header = new FilePacket(Protocol.TYPE_FILE);
        header.setFileName(file.getName());
        header.setFileSize(fileSize);
        header.setMimeType(mimeType);

        out.write((MessageParser.toJson(header) + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();

        // Gửi nội dung nhị phân (binary chunks)
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[Protocol.CHUNK_SIZE];
            long totalSent = 0;
            int read;
            
            while ((read = fis.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalSent += read;
                
                if (listener != null) {
                    int percent = (int) ((totalSent * 100) / fileSize);
                    listener.onProgress(percent);
                }
            }
            out.flush();
        }
    }
}
