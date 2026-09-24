package com.tcpchat.server.network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.tcpchat.common.model.FilePacket;
import com.tcpchat.common.model.Message;
import com.tcpchat.common.model.TextResult;

/**
 * Chuyển đổi giữa JSON string (NDJSON) và Java objects.
 * <p>Stateless utility class — thread-safe, không giữ trạng thái.</p>
 * <p>Không xử lý logic nghiệp vụ: chỉ parse/serialize.</p>
 */
public class MessageParser {

    private static final Gson GSON = new Gson();

    private MessageParser() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Phát hiện loại message từ JSON mà không cần deserialize toàn bộ.
     *
     * @param jsonLine Chuỗi JSON (1 dòng NDJSON)
     * @return Giá trị trường "type" (ví dụ: "TEXT", "FILE", "PING")
     * @throws IllegalArgumentException nếu JSON không hợp lệ hoặc thiếu trường "type"
     */
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

            String type = typeElement.getAsString();
            if (type.isBlank()) {
                throw new IllegalArgumentException("Trường 'type' rỗng: " + jsonLine);
            }

            return type;
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("JSON không hợp lệ: " + jsonLine, e);
        }
    }

    /**
     * Parse JSON thành Message object (dùng cho TEXT, KEY_EXCHANGE, PING, PONG, ERROR).
     *
     * @param jsonLine Chuỗi JSON
     * @return Message object
     * @throws IllegalArgumentException nếu JSON không hợp lệ
     */
    public static Message parseMessage(String jsonLine) {
        if (jsonLine == null || jsonLine.isBlank()) {
            throw new IllegalArgumentException("JSON input không được null hoặc rỗng");
        }
        try {
            return GSON.fromJson(jsonLine, Message.class);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Không thể parse Message từ JSON: " + jsonLine, e);
        }
    }

    /**
     * Parse JSON thành FilePacket object (dùng cho FILE, FILE_ACK).
     *
     * @param jsonLine Chuỗi JSON
     * @return FilePacket object
     * @throws IllegalArgumentException nếu JSON không hợp lệ
     */
    public static FilePacket parseFilePacket(String jsonLine) {
        if (jsonLine == null || jsonLine.isBlank()) {
            throw new IllegalArgumentException("JSON input không được null hoặc rỗng");
        }
        try {
            return GSON.fromJson(jsonLine, FilePacket.class);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Không thể parse FilePacket từ JSON: " + jsonLine, e);
        }
    }

    /**
     * Serialize TextResult thành JSON (single-line cho NDJSON).
     *
     * @param result TextResult object
     * @return JSON string không chứa newline
     */
    public static String toJson(TextResult result) {
        return GSON.toJson(result);
    }

    /**
     * Serialize Message thành JSON (single-line cho NDJSON).
     *
     * @param message Message object
     * @return JSON string không chứa newline
     */
    public static String toJson(Message message) {
        return GSON.toJson(message);
    }

    /**
     * Serialize FilePacket thành JSON (single-line cho NDJSON).
     *
     * @param filePacket FilePacket object
     * @return JSON string không chứa newline
     */
    public static String toJson(FilePacket filePacket) {
        return GSON.toJson(filePacket);
    }
}
