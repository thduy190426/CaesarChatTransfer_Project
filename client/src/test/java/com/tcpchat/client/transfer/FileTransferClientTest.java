package com.tcpchat.client.transfer;

import com.tcpchat.client.ProgressListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FileTransferClientTest {

    @Test
    @DisplayName("Gửi file 16KB chia thành nhiều chunk 8KB và hoàn thành 100%")
    public void testSendFileChunking() throws IOException {
        File tempFile = File.createTempFile("test_transfer", ".dat");
        tempFile.deleteOnExit();

        byte[] dummyData = new byte[16384]; 
        for (int i = 0; i < dummyData.length; i++) {
            dummyData[i] = (byte) (i % 256);
        }
        
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(dummyData);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileTransferClient client = new FileTransferClient(out);
        
        List<Integer> progressUpdates = new ArrayList<>();
        client.sendFile(tempFile, progressUpdates::add);

        byte[] sentData = out.toByteArray();
        
        assertTrue(sentData.length > 16384, "Dữ liệu gửi đi phải bao gồm JSON header + nội dung file");
        String outputStr = new String(sentData, 0, Math.min(sentData.length, 300), StandardCharsets.UTF_8);
        assertTrue(outputStr.contains("\"type\":\"FILE\""));
        assertTrue(outputStr.contains("\"fileSize\":16384"));
        
        assertFalse(progressUpdates.isEmpty());
        assertEquals(100, progressUpdates.get(progressUpdates.size() - 1), "Tiến trình cuối cùng phải đạt 100%");
    }

    @Test
    @DisplayName("Gửi file nhỏ hơn 1 chunk (< 8192 bytes)")
    public void testSendSmallFile_LessThanOneChunk() throws IOException {
        File tempFile = File.createTempFile("small_file", ".txt");
        tempFile.deleteOnExit();

        byte[] content = "Hello World TCP Chat Transfer Test".getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(content);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileTransferClient client = new FileTransferClient(out);

        List<Integer> progressList = new ArrayList<>();
        client.sendFile(tempFile, progressList::add);

        byte[] sentData = out.toByteArray();
        assertTrue(sentData.length > content.length);
        assertEquals(100, progressList.get(progressList.size() - 1));
    }

    @Test
    @DisplayName("Gửi file có kích thước lẻ không chia hết cho CHUNK_SIZE (10,000 bytes)")
    public void testSendFile_NonMultipleOfChunkSize() throws IOException {
        File tempFile = File.createTempFile("odd_size", ".bin");
        tempFile.deleteOnExit();

        byte[] dummyData = new byte[10000];
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(dummyData);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileTransferClient client = new FileTransferClient(out);

        List<Integer> progressList = new ArrayList<>();
        client.sendFile(tempFile, progressList::add);

        assertTrue(progressList.size() >= 2, "File 10KB phải gửi qua ít nhất 2 chunks");
        assertEquals(100, progressList.get(progressList.size() - 1));
    }

    @Test
    @DisplayName("Kiểm tra cơ chế Streaming với file lớn (10MB) không gây OutOfMemory (OOM)")
    public void testSendLargeFile_StreamingNoOOM() throws IOException {
        File tempFile = File.createTempFile("large_test", ".dat");
        tempFile.deleteOnExit();

        int targetSize = 10 * 1024 * 1024; // 10MB
        try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
            raf.setLength(targetSize);
        }

        // Tạo OutputStream đếm số byte gửi thay vì lưu toàn bộ vào RAM
        OutputStream nullCountingStream = new OutputStream() {
            long count = 0;
            @Override
            public void write(int b) { count++; }
            @Override
            public void write(byte[] b, int off, int len) { count += len; }
        };

        FileTransferClient client = new FileTransferClient(nullCountingStream);
        List<Integer> progressList = new ArrayList<>();

        assertDoesNotThrow(() -> client.sendFile(tempFile, progressList::add));
        assertFalse(progressList.isEmpty());
        assertEquals(100, progressList.get(progressList.size() - 1));
    }

    @Test
    @DisplayName("Ném ngoại lệ IOException khi file không tồn tại")
    public void testSendFile_NonExistentFileThrowsException() {
        File nonExistent = new File("non_existent_file_12345.xyz");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileTransferClient client = new FileTransferClient(out);

        assertThrows(IOException.class, () -> client.sendFile(nonExistent, null));
    }

    @Test
    @DisplayName("Ném ngoại lệ và dừng lại khi OutputStream gặp lỗi kết nối giữa chừng")
    public void testSendFile_StreamErrorMidTransfer() throws IOException {
        File tempFile = File.createTempFile("fail_transfer", ".dat");
        tempFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(new byte[16384]);
        }

        OutputStream brokenStream = new OutputStream() {
            private int writeCount = 0;
            @Override
            public void write(int b) throws IOException {
                if (++writeCount > 500) {
                    throw new IOException("Mô phỏng đứt kết nối mạng");
                }
            }
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                if (++writeCount > 1) {
                    throw new IOException("Mô phỏng đứt kết nối mạng");
                }
            }
        };

        FileTransferClient client = new FileTransferClient(brokenStream);
        assertThrows(IOException.class, () -> client.sendFile(tempFile, null));
    }
}
