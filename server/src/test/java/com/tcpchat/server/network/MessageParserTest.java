package com.tcpchat.server.network;

import com.tcpchat.common.Protocol;
import com.tcpchat.common.model.FilePacket;
import com.tcpchat.common.model.Message;
import com.tcpchat.common.model.TextResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageParserTest {

    // ===== detectType() =====

    @Test
    void detectType_textMessage_returnsTextType() {
        String json = "{\"type\":\"TEXT\",\"cipherText\":\"khoor\",\"key\":3}";
        assertEquals(Protocol.TYPE_TEXT, MessageParser.detectType(json));
    }

    @Test
    void detectType_pingMessage_returnsPingType() {
        String json = "{\"type\":\"PING\"}";
        assertEquals(Protocol.TYPE_PING, MessageParser.detectType(json));
    }

    @Test
    void detectType_fileMessage_returnsFileType() {
        String json = "{\"type\":\"FILE\",\"fileName\":\"test.txt\",\"fileSize\":1024}";
        assertEquals(Protocol.TYPE_FILE, MessageParser.detectType(json));
    }

    @Test
    void detectType_malformedJson_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.detectType("not valid json {{{"));
    }

    @Test
    void detectType_missingType_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.detectType("{\"cipherText\":\"hello\"}"));
    }

    @Test
    void detectType_nullInput_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.detectType(null));
    }

    @Test
    void detectType_whitespaceOnly_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.detectType("   "));
    }

    @Test
    void detectType_objectType_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.detectType("{\"type\":{}}"));
    }

    @Test
    void detectType_arrayType_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.detectType("{\"type\":[]}"));
    }

    @Test
    void detectType_primitiveNonStringType_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.detectType("{\"type\":123}"));
    }

    @Test
    void detectType_blankType_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.detectType("{\"type\":\"   \"}"));
    }

    @Test
    void detectType_jsonNullLiteral_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.detectType("null"));
    }

    // ===== parseMessage() =====

    @Test
    void parseMessage_textMessage_parsesCorrectly() {
        String json = "{\"type\":\"TEXT\",\"cipherText\":\"khoor\",\"key\":3}";
        Message msg = MessageParser.parseMessage(json);
        assertEquals("TEXT", msg.getType());
        assertEquals("khoor", msg.getCipherText());
        assertEquals(3, msg.getKey());
    }

    @Test
    void parseMessage_keyExchange_parsesKey() {
        String json = "{\"type\":\"KEY_EXCHANGE\",\"key\":7}";
        Message msg = MessageParser.parseMessage(json);
        assertEquals("KEY_EXCHANGE", msg.getType());
        assertEquals(7, msg.getKey());
    }

    @Test
    void parseMessage_pong_parsesType() {
        String json = "{\"type\":\"PONG\"}";
        Message msg = MessageParser.parseMessage(json);
        assertEquals("PONG", msg.getType());
    }

    @Test
    void parseMessage_nullInput_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.parseMessage(null));
    }

    @Test
    void parseMessage_emptyString_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.parseMessage(""));
    }

    @Test
    void parseMessage_whitespaceOnly_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.parseMessage("   "));
    }

    @Test
    void parseMessage_malformedJson_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.parseMessage("not valid json {{{"));
    }

    @Test
    void parseMessage_jsonNullLiteral_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.parseMessage("null"));
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.parseMessage("  null  "));
    }

    // ===== parseFilePacket() =====

    @Test
    void parseFilePacket_validFile_parsesCorrectly() {
        String json = "{\"type\":\"FILE\",\"fileName\":\"photo.jpg\",\"fileSize\":204800,\"mimeType\":\"image/jpeg\"}";
        FilePacket fp = MessageParser.parseFilePacket(json);
        assertEquals("FILE", fp.getType());
        assertEquals("photo.jpg", fp.getFileName());
        assertEquals(204800L, fp.getFileSize());
        assertEquals("image/jpeg", fp.getMimeType());
    }

    @Test
    void parseFilePacket_nullInput_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.parseFilePacket(null));
    }

    @Test
    void parseFilePacket_emptyString_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.parseFilePacket(""));
    }

    @Test
    void parseFilePacket_whitespaceOnly_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.parseFilePacket("   "));
    }

    @Test
    void parseFilePacket_malformedJson_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.parseFilePacket("not valid json {{{"));
    }

    @Test
    void parseFilePacket_jsonNullLiteral_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.parseFilePacket("null"));
        assertThrows(IllegalArgumentException.class,
                () -> MessageParser.parseFilePacket("  null  "));
    }

    // ===== toJson() =====

    @Test
    void toJson_textResult_producesValidJson() {
        Map<Character, Integer> freq = new HashMap<>();
        freq.put('H', 1);
        freq.put('E', 1);
        freq.put('L', 2);
        TextResult result = new TextResult("HELLO", freq);

        String json = MessageParser.toJson(result);
        assertNotNull(json);
        assertTrue(json.contains("HELLO"));
        assertTrue(json.contains("TEXT_RESULT"));
        // JSON phải nằm trên 1 dòng (NDJSON requirement)
        assertFalse(json.contains("\n"), "JSON output phải là single-line cho NDJSON");
    }

    @Test
    void toJson_message_producesValidJson() {
        Message ping = new Message(Protocol.TYPE_PING);
        String json = MessageParser.toJson(ping);
        assertNotNull(json);
        assertTrue(json.contains("PING"));
        assertFalse(json.contains("\n"));
    }

    @Test
    void toJson_filePacket_producesValidJson() {
        FilePacket ack = new FilePacket(Protocol.TYPE_FILE_ACK);
        ack.setFileName("test.txt");
        ack.setStatus("SUCCESS");
        String json = MessageParser.toJson(ack);
        assertNotNull(json);
        assertTrue(json.contains("FILE_ACK"));
        assertFalse(json.contains("\n"));
    }
}
