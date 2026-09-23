package com.tcpchat.server.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {

    @Test
    void testThreadPoolConstants() {
        assertTrue(ServerConfig.CORE_POOL_SIZE > 0);
        assertTrue(ServerConfig.MAX_POOL_SIZE >= ServerConfig.CORE_POOL_SIZE);
        assertTrue(ServerConfig.THREAD_KEEP_ALIVE_SECONDS > 0);
    }

    @Test
    void testHeartbeatConstants() {
        assertTrue(ServerConfig.HEARTBEAT_INTERVAL_MS > 0);
        assertTrue(ServerConfig.HEARTBEAT_TIMEOUT_MS > ServerConfig.HEARTBEAT_INTERVAL_MS,
                "Timeout phải lớn hơn interval để client có đủ thời gian trả PONG");
        assertTrue(ServerConfig.HEARTBEAT_INITIAL_DELAY_MS > 0);
    }

    @Test
    void testFileTransferConstants() {
        assertNotNull(ServerConfig.UPLOAD_DIR);
        assertFalse(ServerConfig.UPLOAD_DIR.isBlank());
        assertTrue(ServerConfig.MAX_FILE_SIZE > 0);
    }

    @Test
    void testShutdownConstants() {
        assertTrue(ServerConfig.SHUTDOWN_TIMEOUT_SECONDS > 0);
    }
}
