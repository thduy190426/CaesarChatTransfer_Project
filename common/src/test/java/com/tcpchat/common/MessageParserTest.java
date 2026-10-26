package com.tcpchat.common;

import com.tcpchat.common.model.FilePacket;
import com.tcpchat.common.model.Message;
import com.tcpchat.common.model.TextResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MessageParserTest {

    @Test
    @DisplayName("Serialize và deserialize Message loại TEXT")
    public void testSerializeDeserializeMessage() {
        Message original = new Message(Protocol.TYPE_TEXT);
        original.setCipherText("KHOOR");
        original.setKey(3);
        
        String json = MessageParser.toJson(original);
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"TEXT\""));
        assertTrue(json.contains("\"cipherText\":\"KHOOR\""));
        
        Message parsed = MessageParser.parseMessage(json);
        assertNotNull(parsed);
        assertEquals(Protocol.TYPE_TEXT, parsed.getType());
        assertEquals("KHOOR", parsed.getCipherText());
        assertEquals(3, parsed.getKey());
    }

    @Test
    @DisplayName("Serialize và deserialize Message loại KEY_EXCHANGE")
    public void testSerializeDeserializeKeyExchange() {
        Message original = new Message(Protocol.TYPE_KEY_EXCHANGE);
        original.setKey(7);

        String json = MessageParser.toJson(original);
        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"KEY_EXCHANGE\""));

        Message parsed = MessageParser.parseMessage(json);
        assertNotNull(parsed);
        assertEquals(Protocol.TYPE_KEY_EXCHANGE, parsed.getType());
        assertEquals(7, parsed.getKey());
    }

    @Test
    @DisplayName("Serialize và deserialize Message loại PING và PONG (Heartbeat)")
    public void testSerializeDeserializeHeartbeat() {
        Message ping = new Message(Protocol.TYPE_PING);
        String pingJson = MessageParser.toJson(ping);
        Message parsedPing = MessageParser.parseMessage(pingJson);
        assertEquals(Protocol.TYPE_PING, parsedPing.getType());

        Message pong = new Message(Protocol.TYPE_PONG);
        String pongJson = MessageParser.toJson(pong);
        Message parsedPong = MessageParser.parseMessage(pongJson);
        assertEquals(Protocol.TYPE_PONG, parsedPong.getType());
    }

    @Test
    @DisplayName("Serialize và deserialize Message loại ERROR")
    public void testSerializeDeserializeError() {
        Message error = new Message(Protocol.TYPE_ERROR);
        error.setCipherText("Khóa không hợp lệ");

        String json = MessageParser.toJson(error);
        Message parsed = MessageParser.parseMessage(json);
        assertNotNull(parsed);
        assertEquals(Protocol.TYPE_ERROR, parsed.getType());
        assertEquals("Khóa không hợp lệ", parsed.getCipherText());
    }

    @Test
    @DisplayName("Serialize và deserialize FilePacket cho luồng gửi file")
    public void testSerializeDeserializeFilePacket() {
        FilePacket original = new FilePacket(Protocol.TYPE_FILE);
        original.setFileName("test.txt");
        original.setFileSize(1024L);
        original.setMimeType("text/plain");

        String json = MessageParser.toJson(original);
        assertNotNull(json);

        FilePacket parsed = MessageParser.parseFilePacket(json);
        assertNotNull(parsed);
        assertEquals(Protocol.TYPE_FILE, parsed.getType());
        assertEquals("test.txt", parsed.getFileName());
        assertEquals(1024L, parsed.getFileSize());
        assertEquals("text/plain", parsed.getMimeType());
    }

    @Test
    @DisplayName("Serialize và deserialize TextResult chứa kết quả giải mã và tần suất")
    public void testSerializeDeserializeTextResult() {
        Map<Character, Integer> freq = new HashMap<>();
        freq.put('H', 1);
        freq.put('E', 1);
        freq.put('L', 2);
        freq.put('O', 1);

        TextResult original = new TextResult("HELLO", freq);
        
        String json = MessageParser.toJson(original);
        assertNotNull(json);

        TextResult parsed = MessageParser.parseTextResult(json);
        assertNotNull(parsed);
        assertEquals(Protocol.TYPE_TEXT_RESULT, parsed.getType());
        assertEquals("HELLO", parsed.getPlainText());
        assertNotNull(parsed.getFrequency());
        assertEquals(1, parsed.getFrequency().get('H'));
        assertEquals(2, parsed.getFrequency().get('L'));
    }

    @Test
    @DisplayName("Xử lý an toàn khi parse chuỗi null hoặc rỗng")
    public void testParseNullOrEmpty() {
        assertNull(MessageParser.parseMessage(null));
        assertNull(MessageParser.parseMessage(""));
        assertNull(MessageParser.parseFilePacket(null));
        assertNull(MessageParser.parseTextResult(null));
    }
}
