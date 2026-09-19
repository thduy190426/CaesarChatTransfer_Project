package com.tcpchat.common.model;

/**
 * Model đại diện cho thông điệp giao tiếp chung giữa Client và Server (Text/Key/Error/Heartbeat).
 */
public class Message {
    private String type;
    private String cipherText;
    private Integer key;
    private String message; // Cho thông báo lỗi
    private String status;  // Cho các ACK

    public Message() {
    }

    public Message(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCipherText() {
        return cipherText;
    }

    public void setCipherText(String cipherText) {
        this.cipherText = cipherText;
    }

    public Integer getKey() {
        return key;
    }

    public void setKey(Integer key) {
        this.key = key;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
