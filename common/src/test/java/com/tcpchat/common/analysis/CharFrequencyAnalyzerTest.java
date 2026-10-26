package com.tcpchat.common.analysis;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CharFrequencyAnalyzerTest {

    @Test
    @DisplayName("Đếm tần suất tất cả ký tự bao gồm chữ cái, khoảng trắng, số, ký tự đặc biệt")
    public void testAnalyzeAllCharacters() {
        String text = "hello world! 123";
        Map<Character, Integer> freq = CharFrequencyAnalyzer.analyze(text);
        
        assertNotNull(freq);
        assertEquals(3, freq.get('l')); 
        assertEquals(2, freq.get('o'));
        assertEquals(2, freq.get(' '));
        assertEquals(1, freq.get('!'));
        assertEquals(1, freq.get('1'));
    }

    @Test
    @DisplayName("Chỉ đếm các chữ cái a-z và A-Z, bỏ qua chữ số và dấu câu")
    public void testAnalyzeLettersOnly() {
        String text = "Hello World! 123";
        Map<Character, Integer> freq = CharFrequencyAnalyzer.analyzeLettersOnly(text);
        
        assertNotNull(freq);
        assertEquals(1, freq.get('H'));
        assertEquals(3, freq.get('l'));
        assertEquals(2, freq.get('o'));
        
        assertNull(freq.get(' '));
        assertNull(freq.get('!'));
        assertNull(freq.get('1'));
    }

    @Test
    @DisplayName("Phân biệt chính xác giữa chữ hoa và chữ thường (Case Sensitivity)")
    public void testCaseSensitivity() {
        String text = "aA bB cC aA";
        Map<Character, Integer> freq = CharFrequencyAnalyzer.analyzeLettersOnly(text);

        assertEquals(2, freq.get('a'));
        assertEquals(2, freq.get('A'));
        assertEquals(1, freq.get('b'));
        assertEquals(1, freq.get('B'));
    }

    @Test
    @DisplayName("Xử lý an toàn khi đầu vào là chuỗi rỗng")
    public void testAnalyzeEmptyString() {
        Map<Character, Integer> freq1 = CharFrequencyAnalyzer.analyze("");
        assertNotNull(freq1);
        assertTrue(freq1.isEmpty());

        Map<Character, Integer> freq2 = CharFrequencyAnalyzer.analyzeLettersOnly("");
        assertNotNull(freq2);
        assertTrue(freq2.isEmpty());
    }

    @Test
    @DisplayName("Xử lý an toàn khi đầu vào là null")
    public void testAnalyzeNullString() {
        Map<Character, Integer> freq1 = CharFrequencyAnalyzer.analyze(null);
        assertNotNull(freq1);
        assertTrue(freq1.isEmpty());

        Map<Character, Integer> freq2 = CharFrequencyAnalyzer.analyzeLettersOnly(null);
        assertNotNull(freq2);
        assertTrue(freq2.isEmpty());
    }

    @Test
    @DisplayName("Kiểm tra serialize sang JSON và parse ngược lại chính xác")
    public void testToJsonAndDeserialize() {
        Map<Character, Integer> freq = CharFrequencyAnalyzer.analyze("aabbbc");
        String json = CharFrequencyAnalyzer.toJson(freq);
        assertNotNull(json);
        assertTrue(json.contains("\"a\":2") || json.contains("\"a\": 2"));
        assertTrue(json.contains("\"b\":3") || json.contains("\"b\": 3"));
        assertTrue(json.contains("\"c\":1") || json.contains("\"c\": 1"));

        Gson gson = new Gson();
        Type type = new TypeToken<Map<Character, Integer>>(){}.getType();
        Map<Character, Integer> restored = gson.fromJson(json, type);
        assertEquals(3, restored.size());
        assertEquals(2, restored.get('a'));
        assertEquals(3, restored.get('b'));
        assertEquals(1, restored.get('c'));
    }

    @Test
    @DisplayName("Kiểm tra serialize map null trả về chuỗi rỗng '{}'")
    public void testToJsonNullMap() {
        String json = CharFrequencyAnalyzer.toJson(null);
        assertEquals("{}", json);
    }

    @Test
    @DisplayName("Kiểm tra xử lý văn bản dung lượng lớn (100,000 ký tự)")
    public void testLargeTextPerformance() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            sb.append("abcde");
        }
        String largeText = sb.toString();
        
        long startTime = System.currentTimeMillis();
        Map<Character, Integer> freq = CharFrequencyAnalyzer.analyze(largeText);
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull(freq);
        assertEquals(20000, freq.get('a'));
        assertEquals(20000, freq.get('e'));
        assertTrue(duration < 1000, "Phân tích 100,000 ký tự phải hoàn tất trong vòng 1 giây");
    }
}
