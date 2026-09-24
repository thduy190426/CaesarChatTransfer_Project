package com.tcpchat.server.network;

import com.tcpchat.server.config.ServerConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class HeartbeatTaskTest {

    /**
     * Fake implementation of HeartbeatTarget for testing.
     */
    static class FakeTarget implements HeartbeatTask.HeartbeatTarget {
        final List<String> sentMessages = new ArrayList<>();
        final AtomicBoolean disconnected = new AtomicBoolean(false);
        final AtomicLong lastPongTime = new AtomicLong(System.currentTimeMillis());

        @Override
        public void sendMessage(String jsonLine) {
            sentMessages.add(jsonLine);
        }

        @Override
        public void disconnect() {
            disconnected.set(true);
        }

        @Override
        public long getLastPongTime() {
            return lastPongTime.get();
        }

        @Override
        public String getClientId() {
            return "127.0.0.1:12345";
        }
    }

    @Test
    void run_whenPongRecent_sendsPingAndDoesNotDisconnect() {
        FakeTarget target = new FakeTarget();
        // lastPongTime = now → PONG mới nhận gần đây
        target.lastPongTime.set(System.currentTimeMillis());

        HeartbeatTask task = new HeartbeatTask(target);
        task.run();

        assertEquals(1, target.sentMessages.size(), "Phải gửi đúng 1 PING");
        assertTrue(target.sentMessages.get(0).contains("PING"), "Message phải chứa PING");
        assertFalse(target.disconnected.get(), "Không được disconnect khi PONG còn mới");
    }

    @Test
    void run_whenPongTimedOut_disconnectsClient() {
        FakeTarget target = new FakeTarget();
        // Giả lập PONG cũ hơn timeout threshold
        target.lastPongTime.set(System.currentTimeMillis() - ServerConfig.HEARTBEAT_TIMEOUT_MS - 1000);

        HeartbeatTask task = new HeartbeatTask(target);
        task.run();

        assertTrue(target.disconnected.get(), "Phải disconnect khi PONG timeout");
    }

    @Test
    void run_whenPongExactlyAtTimeout_doesNotDisconnect() {
        FakeTarget target = new FakeTarget();
        // Đúng tại ranh giới timeout — chưa vượt quá nên KHÔNG disconnect
        target.lastPongTime.set(System.currentTimeMillis() - ServerConfig.HEARTBEAT_TIMEOUT_MS);

        HeartbeatTask task = new HeartbeatTask(target);
        task.run();

        assertFalse(target.disconnected.get(), "Không disconnect khi chưa vượt quá timeout");
    }
}
