package com.tcpchat.common.crypto;

/**
 * Lớp cung cấp phương thức mã hóa và giải mã theo thuật toán Caesar Cipher.
 * Chỉ xoay vòng (shift) đối với các ký tự chữ cái (a-z, A-Z), các ký tự khác được giữ nguyên.
 */
public class CaesarCipher {

    /**
     * Mã hóa văn bản với khóa cho trước.
     *
     * @param text Văn bản rõ cần mã hóa
     * @param key  Khóa mã hóa (từ 1 đến 25)
     * @return Văn bản đã được mã hóa (Ciphertext)
     * @throws IllegalArgumentException nếu khóa không nằm trong khoảng 1-25
     */
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
                result.append(ch); // Giữ nguyên ký tự đặc biệt, số, khoảng trắng
            }
        }
        return result.toString();
    }

    /**
     * Giải mã văn bản mã hóa với khóa cho trước.
     *
     * @param cipherText Văn bản mã hóa (Ciphertext)
     * @param key        Khóa đã dùng để mã hóa (từ 1 đến 25)
     * @return Văn bản rõ (Plaintext)
     * @throws IllegalArgumentException nếu khóa không nằm trong khoảng 1-25
     */
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
