package com.tcpchat.server.network;

import com.tcpchat.common.Protocol;
import com.tcpchat.common.model.Message;
import com.tcpchat.server.config.ServerConfig;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HeartbeatTask implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatTask.class);

    
    public interface HeartbeatTarget {
        
        void sendMessage(String jsonLine);
        
        void disconnect();
        
        long getLastPongTime();
        
        String getClientId();
    }

    private final HeartbeatTarget target;

    public HeartbeatTask(HeartbeatTarget target) {
        this.target = Objects.requireNonNull(target, "HeartbeatTarget must not be null");
    }

    @Override
    public void run() {
        try {
            
            Message ping = new Message(Protocol.TYPE_PING);
            String pingJson = MessageParser.toJson(ping);
            target.sendMessage(pingJson);

            
            long elapsed = System.currentTimeMillis() - target.getLastPongTime();
            if (elapsed > ServerConfig.HEARTBEAT_TIMEOUT_MS) {
                logger.warn("Client {} heartbeat timeout ({}ms không có PONG). Đang ngắt kết nối...",
                        target.getClientId(), elapsed);
                target.disconnect();
            }
        } catch (Exception e) {
            
            logger.error("Lỗi trong HeartbeatTask cho client {}: {}",
                    target.getClientId(), e.getMessage(), e);
        }
    }
}
