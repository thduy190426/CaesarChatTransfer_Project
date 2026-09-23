package com.tcpchat.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tcpchat.common.MessageParser;
import com.tcpchat.common.Protocol;
import com.tcpchat.common.model.FilePacket;
import com.tcpchat.common.model.Message;
import com.tcpchat.common.model.TextResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MessageReceiver implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MessageReceiver.class);
    
    private final BufferedReader reader;
    private final ClientConnection connection;
    private final MessageListener listener;
    private volatile boolean isRunning = true;

    public MessageReceiver(InputStream in, ClientConnection connection, MessageListener listener) {
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.connection = connection;
        this.listener = listener;
    }

    @Override
    public void run() {
        try {
            while (isRunning) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.trim().isEmpty()) continue;

                JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                if (!json.has("type")) continue;
                
                String type = json.get("type").getAsString();
                
                switch (type) {
                    case Protocol.TYPE_PING:
                        connection.sendPong();
                        break;
                    case Protocol.TYPE_TEXT_RESULT:
                        TextResult textResult = MessageParser.parseTextResult(line);
                        SwingUtilities.invokeLater(() -> listener.onTextResultReceived(textResult));
                        break;
                    case Protocol.TYPE_FILE_ACK:
                        FilePacket fileAck = MessageParser.parseFilePacket(line);
                        SwingUtilities.invokeLater(() -> listener.onFileAckReceived(fileAck));
                        break;
                    case Protocol.TYPE_ERROR:
                        Message error = MessageParser.parseMessage(line);
                        SwingUtilities.invokeLater(() -> listener.onErrorReceived(error));
                        break;
                    default:
                        logger.warn("Loại tin nhắn không xác định: {}", type);
                        break;
                }
            }
        } catch (IOException e) {
            if (isRunning) {
                logger.error("Lỗi đọc dữ liệu từ server", e);
            }
        } finally {
            SwingUtilities.invokeLater(listener::onDisconnected);
        }
    }

    public void stop() {
        isRunning = false;
        try {
            reader.close();
        } catch (IOException e) {
            logger.error("Lỗi khi đóng luồng đọc", e);
        }
    }
}
