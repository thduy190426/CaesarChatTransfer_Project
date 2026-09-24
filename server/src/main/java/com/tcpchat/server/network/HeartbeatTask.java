package com.tcpchat.server.network;

import com.tcpchat.common.Protocol;
import com.tcpchat.common.model.Message;
import com.tcpchat.server.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tác vụ gửi PING định kỳ và kiểm tra timeout PONG.
 * <p>Được chạy bởi {@link java.util.concurrent.ScheduledExecutorService}
 * với chu kỳ {@link ServerConfig#HEARTBEAT_INTERVAL_MS}.</p>
 *
 * <p>Thiết kế theo Interface Segregation: phụ thuộc vào {@link HeartbeatTarget}
 * thay vì trực tiếp {@code ClientHandlerThread}, cho phép test độc lập.</p>
 */
public class HeartbeatTask implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatTask.class);

    /**
     * Interface tối thiểu mà HeartbeatTask cần từ handler.
     */
    public interface HeartbeatTarget {
        /** Gửi JSON message qua socket */
        void sendMessage(String jsonLine);
        /** Ngắt kết nối client */
        void disconnect();
        /** Thời điểm nhận PONG gần nhất (epoch ms) */
        long getLastPongTime();
        /** Định danh client cho logging (ip:port) */
        String getClientId();
    }

    private final HeartbeatTarget target;

    public HeartbeatTask(HeartbeatTarget target) {
        this.target = target;
    }

    @Override
    public void run() {
        try {
            // 1. Gửi PING
            Message ping = new Message(Protocol.TYPE_PING);
            String pingJson = MessageParser.toJson(ping);
            target.sendMessage(pingJson);

            // 2. Kiểm tra timeout
            long elapsed = System.currentTimeMillis() - target.getLastPongTime();
            if (elapsed > ServerConfig.HEARTBEAT_TIMEOUT_MS) {
                logger.warn("Client {} heartbeat timeout ({}ms không có PONG). Đang ngắt kết nối...",
                        target.getClientId(), elapsed);
                target.disconnect();
            }
        } catch (Exception e) {
            // Heartbeat lỗi không được crash scheduler
            logger.error("Lỗi trong HeartbeatTask cho client {}: {}",
                    target.getClientId(), e.getMessage());
        }
    }
}
