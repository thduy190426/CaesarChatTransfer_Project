package com.tcpchat.client;

import com.tcpchat.common.Protocol;
import com.tcpchat.common.model.FilePacket;
import com.tcpchat.common.model.Message;
import com.tcpchat.common.model.TextResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class MessageReceiverTest {

    @Test
    public void testMessageReceiverParsesTextResult() throws InterruptedException {
        String jsonInput = "{\"type\":\"TEXT_RESULT\",\"plainText\":\"HELLO\",\"frequency\":{\"H\":1,\"E\":1,\"L\":2,\"O\":1}}\n";
        InputStream in = new ByteArrayInputStream(jsonInput.getBytes(StandardCharsets.UTF_8));
        
        CountDownLatch latch = new CountDownLatch(1);
        final TextResult[] receivedResult = new TextResult[1];

        MessageListener dummyListener = new MessageListener() {
            @Override
            public void onTextResultReceived(TextResult result) {
                receivedResult[0] = result;
                latch.countDown();
            }

            @Override
            public void onFileAckReceived(FilePacket ack) { }

            @Override
            public void onErrorReceived(Message error) { }

            @Override
            public void onDisconnected() { }
        };

        MessageReceiver receiver = new MessageReceiver(in, null, dummyListener);
        Thread t = new Thread(receiver);
        t.start();

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        receiver.stop();
        t.interrupt();

        assertTrue(completed, "Listener should be triggered");
        assertNotNull(receivedResult[0]);
        assertEquals("HELLO", receivedResult[0].getPlainText());
        assertEquals(2, receivedResult[0].getFrequency().get('L'));
    }
}
