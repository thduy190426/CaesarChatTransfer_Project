package com.tcpchat.client.transfer;

import com.tcpchat.common.Protocol;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class FileTransferClientTest {

    @Test
    public void testSendFileChunking() throws IOException {
        // Create a dummy file of 16KB
        File tempFile = File.createTempFile("test_transfer", ".dat");
        tempFile.deleteOnExit();

        byte[] dummyData = new byte[16384]; // 16KB
        for(int i=0; i<dummyData.length; i++){
            dummyData[i] = (byte) (i % 256);
        }
        
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(dummyData);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileTransferClient client = new FileTransferClient(out);
        
        int[] progressUpdates = new int[1];
        
        client.sendFile(tempFile, percent -> progressUpdates[0] = percent);

        byte[] sentData = out.toByteArray();
        
        assertTrue(sentData.length > 16384, "Total sent data should include JSON header + 16KB file");
        String outputStr = new String(sentData, 0, 200, "UTF-8");
        assertTrue(outputStr.contains("\"type\":\"FILE\""));
        assertTrue(outputStr.contains("\"fileSize\":16384"));
        
        // Progress should reach exactly or close to 100%
        assertEquals(100, progressUpdates[0], "Progress should report 100% when finished");
    }
}
