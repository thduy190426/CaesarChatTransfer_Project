package com.tcpchat.common.model;

import java.util.Map;

/**
 * Model chứa kết quả văn bản đã giải mã kèm tần suất ký tự, gửi từ Server về Client.
 */
public class TextResult {
    private String type; // Thường là TEXT_RESULT
    private String plainText;
    private Map<Character, Integer> frequency;

    public TextResult() {
        this.type = "TEXT_RESULT";
    }

    public TextResult(String plainText, Map<Character, Integer> frequency) {
        this.type = "TEXT_RESULT";
        this.plainText = plainText;
        this.frequency = frequency;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPlainText() {
        return plainText;
    }

    public void setPlainText(String plainText) {
        this.plainText = plainText;
    }

    public Map<Character, Integer> getFrequency() {
        return frequency;
    }

    public void setFrequency(Map<Character, Integer> frequency) {
        this.frequency = frequency;
    }
}
