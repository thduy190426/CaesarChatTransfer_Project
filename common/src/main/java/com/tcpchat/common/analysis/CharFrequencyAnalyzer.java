package com.tcpchat.common.analysis;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;


public class CharFrequencyAnalyzer {

    private static final Gson GSON = new Gson();

    
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

    
    public static String toJson(Map<Character, Integer> frequencyMap) {
        if (frequencyMap == null) return "{}";
        return GSON.toJson(frequencyMap);
    }
}
