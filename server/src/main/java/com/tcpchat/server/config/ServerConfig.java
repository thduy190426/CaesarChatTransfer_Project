package com.tcpchat.server.config;

/**
 * Hằng số cấu hình tập trung cho Server Networking.
 * <p>Mọi magic number dùng trong lớp mạng đều tập trung tại đây,
 * giúp dễ dàng tune performance và tránh hardcode rải rác.</p>
 */
public final class ServerConfig {

    private ServerConfig() {
        throw new UnsupportedOperationException("Utility class — không cho khởi tạo");
    }

    // ===== Thread Pool =====
    /** Số thread tối thiểu luôn sẵn sàng trong pool */
    public static final int CORE_POOL_SIZE = 10;
    /** Số thread tối đa khi tải cao */
    public static final int MAX_POOL_SIZE = 50;
    /** Thời gian (giây) thread nhàn rỗi được giữ lại trước khi bị thu hồi */
    public static final long THREAD_KEEP_ALIVE_SECONDS = 60;

    // ===== Heartbeat =====
    /** Khoảng cách giữa các lần gửi PING (ms) */
    public static final long HEARTBEAT_INTERVAL_MS = 10_000;
    /** Thời gian tối đa chờ PONG trước khi coi client là chết (ms) */
    public static final long HEARTBEAT_TIMEOUT_MS = 15_000;
    /** Thời gian chờ sau khi client connect trước khi bắt đầu gửi PING (ms) */
    public static final long HEARTBEAT_INITIAL_DELAY_MS = 5_000;

    // ===== File Transfer =====
    /** Thư mục lưu file upload từ client */
    public static final String UPLOAD_DIR = "uploads";
    /** Kích thước file tối đa được phép (bytes) — 500MB */
    public static final long MAX_FILE_SIZE = 500L * 1024 * 1024;

    // ===== Graceful Shutdown =====
    /** Thời gian tối đa chờ các thread hoàn tất khi shutdown (giây) */
    public static final long SHUTDOWN_TIMEOUT_SECONDS = 30;
}
