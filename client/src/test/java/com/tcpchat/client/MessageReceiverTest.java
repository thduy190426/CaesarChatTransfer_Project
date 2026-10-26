package com.tcpchat.client;

import com.tcpchat.common.Protocol;
import com.tcpchat.common.model.FilePacket;
import com.tcpchat.common.model.Message;
import com.tcpchat.common.model.TextResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class MessageReceiverTest {

    @Test
    @DisplayName("MessageReceiver nhận và parse chính xác tin nhắn TEXT_RESULT")
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

        boolean completed = latch.await(3, TimeUnit.SECONDS);
        receiver.stop();
        t.interrupt();

        assertTrue(completed, "Listener onTextResultReceived phải được kích hoạt");
        assertNotNull(receivedResult[0]);
        assertEquals("HELLO", receivedResult[0].getPlainText());
        assertEquals(2, receivedResult[0].getFrequency().get('L'));
    }

    @Test
    @DisplayName("MessageReceiver nhận và parse chính xác tin nhắn FILE_ACK")
    public void testMessageReceiverParsesFileAck() throws InterruptedException {
        String jsonInput = "{\"type\":\"FILE_ACK\",\"status\":\"OK\",\"savedPath\":\"uploads/123_photo.jpg\"}\n";
        InputStream in = new ByteArrayInputStream(jsonInput.getBytes(StandardCharsets.UTF_8));

        CountDownLatch latch = new CountDownLatch(1);
        final FilePacket[] receivedAck = new FilePacket[1];

        MessageListener dummyListener = new MessageListener() {
            @Override
            public void onTextResultReceived(TextResult result) { }

            @Override
            public void onFileAckReceived(FilePacket ack) {
                receivedAck[0] = ack;
                latch.countDown();
            }

            @Override
            public void onErrorReceived(Message error) { }

            @Override
            public void onDisconnected() { }
        };

        MessageReceiver receiver = new MessageReceiver(in, null, dummyListener);
        Thread t = new Thread(receiver);
        t.start();

        boolean completed = latch.await(3, TimeUnit.SECONDS);
        receiver.stop();
        t.interrupt();

        assertTrue(completed, "Listener onFileAckReceived phải được kích hoạt");
        assertNotNull(receivedAck[0]);
        assertEquals(Protocol.TYPE_FILE_ACK, receivedAck[0].getType());
        assertEquals("OK", receivedAck[0].getStatus());
        assertEquals("uploads/123_photo.jpg", receivedAck[0].getSavedPath());
    }

    @Test
    @DisplayName("MessageReceiver nhận và parse chính xác tin nhắn ERROR")
    public void testMessageReceiverParsesError() throws InterruptedException {
        String jsonInput = "{\"type\":\"ERROR\",\"cipherText\":\"Chưa trao đổi khóa\"}\n";
        InputStream in = new ByteArrayInputStream(jsonInput.getBytes(StandardCharsets.UTF_8));

        CountDownLatch latch = new CountDownLatch(1);
        final Message[] receivedError = new Message[1];

        MessageListener dummyListener = new MessageListener() {
            @Override
            public void onTextResultReceived(TextResult result) { }

            @Override
            public void onFileAckReceived(FilePacket ack) { }

            @Override
            public void onErrorReceived(Message error) {
                receivedError[0] = error;
                latch.countDown();
            }

            @Override
            public void onDisconnected() { }
        };

        MessageReceiver receiver = new MessageReceiver(in, null, dummyListener);
        Thread t = new Thread(receiver);
        t.start();

        boolean completed = latch.await(3, TimeUnit.SECONDS);
        receiver.stop();
        t.interrupt();

        assertTrue(completed, "Listener onErrorReceived phải được kích hoạt");
        assertNotNull(receivedError[0]);
        assertEquals(Protocol.TYPE_ERROR, receivedError[0].getType());
        assertEquals("Chưa trao đổi khóa", receivedError[0].getCipherText());
    }

    @Test
    @DisplayName("MessageReceiver gọi onDisconnected khi luồng kết nối bị đóng (EOF)")
    public void testMessageReceiverTriggersOnDisconnected() throws InterruptedException {
        InputStream emptyIn = new ByteArrayInputStream(new byte[0]);
        CountDownLatch latch = new CountDownLatch(1);

        MessageListener dummyListener = new MessageListener() {
            @Override
            public void onTextResultReceived(TextResult result) { }

            @Override
            public void onFileAckReceived(FilePacket ack) { }

            @Override
            public void onErrorReceived(Message error) { }

            @Override
            public void onDisconnected() {
                latch.countDown();
            }
        };

        MessageReceiver receiver = new MessageReceiver(emptyIn, null, dummyListener);
        Thread t = new Thread(receiver);
        t.start();

        boolean completed = latch.await(3, TimeUnit.SECONDS);
        receiver.stop();
        t.interrupt();

        assertTrue(completed, "onDisconnected phải được gọi khi gặp EOF");
    }

    @Test
    @DisplayName("MessageReceiver bỏ qua dòng trống hoặc tin nhắn không xác định mà không crash")
    public void testMessageReceiverIgnoresInvalidLines() throws InterruptedException {
        String input = "\n   \n{\"type\":\"UNKNOWN_TYPE\"}\n{\"type\":\"TEXT_RESULT\",\"plainText\":\"DONE\"}\n";
        InputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));

        CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean successReceived = new AtomicBoolean(false);

        MessageListener dummyListener = new MessageListener() {
            @Override
            public void onTextResultReceived(TextResult result) {
                if ("DONE".equals(result.getPlainText())) {
                    successReceived.set(true);
                    latch.countDown();
                }
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

        boolean completed = latch.await(3, TimeUnit.SECONDS);
        receiver.stop();
        t.interrupt();

        assertTrue(completed && successReceived.get(), "Phải vượt qua các dòng không hợp lệ và xử lý tiếp tin nhắn chuẩn");
    }
}
