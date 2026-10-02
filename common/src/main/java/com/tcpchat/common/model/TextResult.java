package com.tcpchat.common.model;

import java.util.Map;


public class TextResult {
    private String type; 
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
