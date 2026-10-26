package com.tcpchat.common.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CaesarCipherTest {

    @Test
    @DisplayName("Mã hóa chữ thường cơ bản")
    void testEncrypt_Lowercase() {
        String plain = "hello";
        int key = 3;
        String cipher = CaesarCipher.encrypt(plain, key);
        assertEquals("khoor", cipher);
    }

    @Test
    @DisplayName("Mã hóa chữ hoa cơ bản")
    void testEncrypt_Uppercase() {
        String plain = "WORLD";
        int key = 5;
        String cipher = CaesarCipher.encrypt(plain, key);
        assertEquals("BTWQI", cipher);
    }

    @Test
    @DisplayName("Mã hóa chuỗi hỗn hợp chữ hoa, chữ thường và ký tự đặc biệt")
    void testEncrypt_MixedAndSpecial() {
        String plain = "Hello, World! 123";
        int key = 7;
        String cipher = CaesarCipher.encrypt(plain, key);
        assertEquals("Olssv, Dvysk! 123", cipher);
    }

    @Test
    @DisplayName("Giải mã chuỗi hỗn hợp chữ hoa, chữ thường và ký tự đặc biệt")
    void testDecrypt_MixedAndSpecial() {
        String cipher = "Olssv, Dvysk! 123";
        int key = 7;
        String plain = CaesarCipher.decrypt(cipher, key);
        assertEquals("Hello, World! 123", plain);
    }

    @Test
    @DisplayName("Kiểm tra giá trị biên hợp lệ nhỏ nhất: Key = 1")
    void testBoundaryKey_MinValidKey() {
        String original = "abcXYZ";
        String cipher = CaesarCipher.encrypt(original, 1);
        assertEquals("bcdYZA", cipher);
        assertEquals(original, CaesarCipher.decrypt(cipher, 1));
    }

    @Test
    @DisplayName("Kiểm tra giá trị biên hợp lệ lớn nhất: Key = 25")
    void testBoundaryKey_MaxValidKey() {
        String original = "abcXYZ";
        String cipher = CaesarCipher.encrypt(original, 25);
        assertEquals("zabWXY", cipher);
        assertEquals(original, CaesarCipher.decrypt(cipher, 25));
    }

    @Test
    @DisplayName("Kiểm tra toàn bộ các khóa hợp lệ từ 1 đến 25 (Roundtrip test)")
    void testAllValidKeys_Roundtrip() {
        String original = "The Quick Brown Fox Jumps Over The Lazy Dog! 12345 @#$%";
        for (int key = 1; key <= 25; key++) {
            String cipher = CaesarCipher.encrypt(original, key);
            assertNotEquals(original, cipher, "Bản mã phải khác bản rõ khi key hợp lệ: key=" + key);
            String decrypted = CaesarCipher.decrypt(cipher, key);
            assertEquals(original, decrypted, "Giải mã phải phục hồi chính xác bản rõ với key=" + key);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 26, 27, 50, 100, -1, -5, -25, -26, Integer.MAX_VALUE, Integer.MIN_VALUE})
    @DisplayName("Ném ngoại lệ IllegalArgumentException với các khóa không hợp lệ ngoài khoảng [1, 25]")
    void testInvalidKeys_ThrowsException(int invalidKey) {
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class, 
                () -> CaesarCipher.encrypt("test", invalidKey),
                "Phải ném ngoại lệ khi mã hóa với key=" + invalidKey);
        assertTrue(e1.getMessage().contains("1 đến 25"));

        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, 
                () -> CaesarCipher.decrypt("test", invalidKey),
                "Phải ném ngoại lệ khi giải mã với key=" + invalidKey);
        assertTrue(e2.getMessage().contains("1 đến 25"));
    }

    @Test
    @DisplayName("Xử lý an toàn khi đầu vào là null")
    void testNullInput_ReturnsNull() {
        assertNull(CaesarCipher.encrypt(null, 5));
        assertNull(CaesarCipher.decrypt(null, 5));
    }

    @Test
    @DisplayName("Xử lý an toàn khi đầu vào là chuỗi rỗng")
    void testEmptyString_ReturnsEmpty() {
        assertEquals("", CaesarCipher.encrypt("", 5));
        assertEquals("", CaesarCipher.decrypt("", 5));
    }

    @Test
    @DisplayName("Giữ nguyên ký tự Unicode, tiếng Việt có dấu và ký tự điều khiển")
    void testUnicodeAndSpecialCharsPreserved() {
        String original = "Xin chào Việt Nam! \n\t Cà phê sữa đá @2026 😊";
        int key = 3;
        String cipher = CaesarCipher.encrypt(original, key);
        assertNotNull(cipher);
        assertTrue(cipher.contains("à") && cipher.contains("ê") && cipher.contains("đ") && cipher.contains("á"));
        assertTrue(cipher.contains("\n") && cipher.contains("\t") && cipher.contains("😊"));
        
        String decrypted = CaesarCipher.decrypt(cipher, key);
        assertEquals(original, decrypted);
    }
}
