package com.tcpchat.common;

/**
 * Các hằng số giao thức chung giữa Client và Server.
 */
public class Protocol {

    public static final int PORT = 9999;
    public static final int CHUNK_SIZE = 8192; // 8KB cho truyền tải file

    // Loại Message (Message Types)
    public static final String TYPE_KEY_EXCHANGE = "KEY_EXCHANGE";
    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_FILE = "FILE";
    public static final String TYPE_TEXT_RESULT = "TEXT_RESULT";
    public static final String TYPE_FILE_ACK = "FILE_ACK";
    public static final String TYPE_ERROR = "ERROR";
    public static final String TYPE_PING = "PING";
    public static final String TYPE_PONG = "PONG";

}
