package com.tcpchat.common.crypto;


public class CaesarCipher {

    
    public static String encrypt(String text, int key) {
        validateKey(key);
        if (text == null) return null;

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isUpperCase(ch)) {
                result.append((char) (((ch - 'A' + key) % 26) + 'A'));
            } else if (Character.isLowerCase(ch)) {
                result.append((char) (((ch - 'a' + key) % 26) + 'a'));
            } else {
                result.append(ch); 
            }
        }
        return result.toString();
    }

    
    public static String decrypt(String cipherText, int key) {
        validateKey(key);
        if (cipherText == null) return null;

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < cipherText.length(); i++) {
            char ch = cipherText.charAt(i);
            if (Character.isUpperCase(ch)) {
                result.append((char) (((ch - 'A' - key + 26) % 26) + 'A'));
            } else if (Character.isLowerCase(ch)) {
                result.append((char) (((ch - 'a' - key + 26) % 26) + 'a'));
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    private static void validateKey(int key) {
        if (key < 1 || key > 25) {
            throw new IllegalArgumentException("Khóa mã hóa (key) phải nằm trong khoảng từ 1 đến 25.");
        }
    }
}
