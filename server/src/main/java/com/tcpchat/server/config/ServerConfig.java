package com.tcpchat.server.config;


public final class ServerConfig {

    private ServerConfig() {
        throw new UnsupportedOperationException("Utility class — không cho khởi tạo");
    }

    
    
    public static final int CORE_POOL_SIZE = 10;
    
    public static final int MAX_POOL_SIZE = 50;
    
    public static final long THREAD_KEEP_ALIVE_SECONDS = 60;

    
    
    public static final long HEARTBEAT_INTERVAL_MS = 10_000;
    
    public static final long HEARTBEAT_TIMEOUT_MS = 15_000;
    
    public static final long HEARTBEAT_INITIAL_DELAY_MS = 5_000;

    
    
    public static final String UPLOAD_DIR = "uploads";
    
    public static final long MAX_FILE_SIZE = 500L * 1024 * 1024;

    
    
    public static final long SHUTDOWN_TIMEOUT_SECONDS = 30;
}
