package com.tcpchat.common.analysis;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CharFrequencyAnalyzerTest {

    @Test
    public void testAnalyzeAllCharacters() {
        String text = "hello world! 123";
        Map<Character, Integer> freq = CharFrequencyAnalyzer.analyze(text);
        
        assertNotNull(freq);
        assertEquals(3, freq.get('l')); // h, e, l, l, o, (space), w, o, r, l, d, !, (space), 1, 2, 3
        assertEquals(2, freq.get('o'));
        assertEquals(2, freq.get(' '));
        assertEquals(1, freq.get('!'));
        assertEquals(1, freq.get('1'));
    }

    @Test
    public void testAnalyzeLettersOnly() {
        String text = "Hello World! 123";
        Map<Character, Integer> freq = CharFrequencyAnalyzer.analyzeLettersOnly(text);
        
        assertNotNull(freq);
        assertEquals(1, freq.get('H'));
        assertEquals(3, freq.get('l'));
        assertEquals(2, freq.get('o'));
        
        // Non-letters should not exist
        assertNull(freq.get(' '));
        assertNull(freq.get('!'));
        assertNull(freq.get('1'));
    }

    @Test
    public void testAnalyzeEmptyString() {
        Map<Character, Integer> freq = CharFrequencyAnalyzer.analyze("");
        assertNotNull(freq);
        assertTrue(freq.isEmpty());
    }

    @Test
    public void testAnalyzeNullString() {
        Map<Character, Integer> freq = CharFrequencyAnalyzer.analyze(null);
        assertNotNull(freq);
        assertTrue(freq.isEmpty());
    }

    @Test
    public void testToJson() {
        Map<Character, Integer> freq = CharFrequencyAnalyzer.analyze("ab");
        String json = CharFrequencyAnalyzer.toJson(freq);
        assertNotNull(json);
        assertTrue(json.contains("\"a\":1") || json.contains("\"a\": 1"));
        assertTrue(json.contains("\"b\":1") || json.contains("\"b\": 1"));
    }
}
