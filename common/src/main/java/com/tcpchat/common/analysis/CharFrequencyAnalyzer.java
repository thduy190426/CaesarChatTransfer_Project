package com.tcpchat.common.analysis;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;

/**
 * Lớp phân tích và đếm tần suất xuất hiện của các ký tự trong văn bản.
 */
public class CharFrequencyAnalyzer {

    private static final Gson GSON = new Gson();

    /**
     * Đếm tần suất tất cả các ký tự trong chuỗi (phân biệt hoa thường).
     *
     * @param text Văn bản cần phân tích
     * @return Map chứa ký tự và số lần xuất hiện
     */
    public static Map<Character, Integer> analyze(String text) {
        Map<Character, Integer> frequencyMap = new HashMap<>();
        if (text == null || text.isEmpty()) {
            return frequencyMap;
        }

        for (char ch : text.toCharArray()) {
            frequencyMap.put(ch, frequencyMap.getOrDefault(ch, 0) + 1);
        }
        return frequencyMap;
    }

    /**
     * Chỉ đếm tần suất các ký tự là chữ cái (a-z, A-Z), bỏ qua các ký tự khác.
     *
     * @param text Văn bản cần phân tích
     * @return Map chứa ký tự (chữ cái) và số lần xuất hiện
     */
    public static Map<Character, Integer> analyzeLettersOnly(String text) {
        Map<Character, Integer> frequencyMap = new HashMap<>();
        if (text == null || text.isEmpty()) {
            return frequencyMap;
        }

        for (char ch : text.toCharArray()) {
            if (Character.isLetter(ch)) {
                frequencyMap.put(ch, frequencyMap.getOrDefault(ch, 0) + 1);
            }
        }
        return frequencyMap;
    }

    /**
     * Serialize Map tần suất sang định dạng JSON để gửi qua TCP.
     *
     * @param frequencyMap Map tần suất cần chuyển đổi
     * @return Chuỗi JSON
     */
    public static String toJson(Map<Character, Integer> frequencyMap) {
        if (frequencyMap == null) return "{}";
        return GSON.toJson(frequencyMap);
    }
}
