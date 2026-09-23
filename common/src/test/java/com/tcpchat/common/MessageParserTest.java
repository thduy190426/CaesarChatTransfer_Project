package com.tcpchat.common;

import com.tcpchat.common.model.FilePacket;
import com.tcpchat.common.model.Message;
import com.tcpchat.common.model.TextResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MessageParserTest {

    @Test
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
}
