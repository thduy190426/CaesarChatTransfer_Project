package com.tcpchat.client;

import com.tcpchat.common.MessageParser;
import com.tcpchat.common.Protocol;
import com.tcpchat.common.model.Message;
import com.tcpchat.client.transfer.FileTransferClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.io.File;

public class ClientConnection {
    private static final Logger logger = LoggerFactory.getLogger(ClientConnection.class);

    private Socket socket;
    private OutputStream out;
    private MessageReceiver receiver;
    private FileTransferClient fileTransferClient;
    private int currentKey = -1;

    public void connect(String host, int port, MessageListener listener) throws IOException {
        socket = new Socket(host, port);
        out = socket.getOutputStream();

        receiver = new MessageReceiver(socket.getInputStream(), this, listener);
        Thread receiverThread = new Thread(receiver);
        receiverThread.setDaemon(true);
        receiverThread.start();
        
        fileTransferClient = new FileTransferClient(out);
        logger.info("Đã kết nối thành công tới {}:{}", host, port);
    }

    public void sendKeyExchange(int key) throws IOException {
        this.currentKey = key;
        Message msg = new Message(Protocol.TYPE_KEY_EXCHANGE);
        msg.setKey(key);
        send(MessageParser.toJson(msg));
    }

    public void sendText(String cipherText) throws IOException {
        Message msg = new Message(Protocol.TYPE_TEXT);
        msg.setCipherText(cipherText);
        msg.setKey(currentKey);
        send(MessageParser.toJson(msg));
    }

    public void sendPong() {
        Message pong = new Message(Protocol.TYPE_PONG);
        try {
            send(MessageParser.toJson(pong));
        } catch (IOException e) {
            logger.warn("Không thể gửi PONG", e);
        }
    }

    public void sendFile(File file, ProgressListener listener) throws IOException {
        if (fileTransferClient != null) {
            fileTransferClient.sendFile(file, listener);
        }
    }

    private synchronized void send(String json) throws IOException {
        if (socket != null && !socket.isClosed()) {
            out.write((json + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    public void close() {
        try {
            if (receiver != null) receiver.stop();
            if (socket != null && !socket.isClosed()) socket.close();
            logger.info("Đã ngắt kết nối với server");
        } catch (IOException e) {
            logger.error("Lỗi khi đóng kết nối", e);
        }
    }
}
