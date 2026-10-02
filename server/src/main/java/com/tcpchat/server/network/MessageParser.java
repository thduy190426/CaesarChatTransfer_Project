package com.tcpchat.server.network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.tcpchat.common.model.FilePacket;
import com.tcpchat.common.model.Message;
import com.tcpchat.common.model.TextResult;


public class MessageParser {

    private static final Gson GSON = new Gson();

    private MessageParser() {
        throw new UnsupportedOperationException("Utility class");
    }

    
    public static String detectType(String jsonLine) {
        if (jsonLine == null || jsonLine.isBlank()) {
            throw new IllegalArgumentException("JSON input không được null hoặc rỗng");
        }

        try {
            JsonElement element = JsonParser.parseString(jsonLine);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("JSON input không phải object: " + jsonLine);
            }

            JsonObject obj = element.getAsJsonObject();
            JsonElement typeElement = obj.get("type");

            if (typeElement == null || typeElement.isJsonNull()) {
                throw new IllegalArgumentException("JSON thiếu trường 'type': " + jsonLine);
            }
            if (!typeElement.isJsonPrimitive() || !typeElement.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Trường 'type' phải là chuỗi hợp lệ: " + jsonLine);
            }

            String type = typeElement.getAsString();
            if (type.isBlank()) {
                throw new IllegalArgumentException("Trường 'type' rỗng: " + jsonLine);
            }

            return type;
        } catch (JsonSyntaxException | IllegalStateException | UnsupportedOperationException e) {
            throw new IllegalArgumentException("JSON không hợp lệ: " + jsonLine, e);
        }
    }

    
    public static Message parseMessage(String jsonLine) {
        if (jsonLine == null || jsonLine.isBlank()) {
            throw new IllegalArgumentException("JSON input không được null hoặc rỗng");
        }
        try {
            Message message = GSON.fromJson(jsonLine, Message.class);
            if (message == null) {
                throw new IllegalArgumentException("JSON không hợp lệ (kết quả null): " + jsonLine);
            }
            return message;
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Không thể parse Message từ JSON: " + jsonLine, e);
        }
    }

    
    public static FilePacket parseFilePacket(String jsonLine) {
        if (jsonLine == null || jsonLine.isBlank()) {
            throw new IllegalArgumentException("JSON input không được null hoặc rỗng");
        }
        try {
            FilePacket packet = GSON.fromJson(jsonLine, FilePacket.class);
            if (packet == null) {
                throw new IllegalArgumentException("JSON không hợp lệ (kết quả null): " + jsonLine);
            }
            return packet;
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Không thể parse FilePacket từ JSON: " + jsonLine, e);
        }
    }

    
    public static String toJson(TextResult result) {
        return GSON.toJson(result);
    }

    
    public static String toJson(Message message) {
        return GSON.toJson(message);
    }

    
    public static String toJson(FilePacket filePacket) {
        return GSON.toJson(filePacket);
    }
}
