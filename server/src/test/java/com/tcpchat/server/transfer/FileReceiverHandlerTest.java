package com.tcpchat.server.transfer;

import com.tcpchat.common.MessageParser;
import com.tcpchat.common.Protocol;
import com.tcpchat.common.model.FilePacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class FileReceiverHandlerTest {

    @BeforeEach
    public void setup() {
        File uploadDir = new File("uploads");
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
    }

    @AfterEach
    public void cleanup() {
        File uploadDir = new File("uploads");
        if (uploadDir.exists()) {
            for (File file : uploadDir.listFiles()) {
                file.delete();
            }
        }
    }

    @Test
    public void testReceiveFileSuccess() throws IOException {
        FilePacket header = new FilePacket(Protocol.TYPE_FILE);
        header.setFileName("dummy.txt");
        header.setFileSize(5L);
        header.setMimeType("text/plain");

        String jsonHeader = MessageParser.toJson(header);
        
        // 5 bytes of data
        byte[] fileData = "Hello".getBytes();
        
        ByteArrayInputStream in = new ByteArrayInputStream(fileData);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        FileReceiverHandler handler = new FileReceiverHandler(in, out, "127.0.0.1");
        
        handler.receiveFile(jsonHeader);

        // Verify output stream has FILE_ACK
        String response = out.toString("UTF-8");
        assertTrue(response.contains("\"type\":\"FILE_ACK\""));
        assertTrue(response.contains("\"status\":\"OK\""));
        
        // Check if file is saved in uploads directory
        File uploadDir = new File("uploads");
        File[] savedFiles = uploadDir.listFiles((dir, name) -> name.endsWith("_dummy.txt"));
        
        assertNotNull(savedFiles);
        assertEquals(1, savedFiles.length);
        assertEquals(5L, savedFiles[0].length());
    }
}
