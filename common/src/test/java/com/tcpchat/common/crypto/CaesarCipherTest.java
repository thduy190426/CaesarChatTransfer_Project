package com.tcpchat.common.crypto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaesarCipherTest {

    @Test
    void testEncrypt_Lowercase() {
        String plain = "hello";
        int key = 3;
        String cipher = CaesarCipher.encrypt(plain, key);
        assertEquals("khoor", cipher);
    }

    @Test
    void testEncrypt_Uppercase() {
        String plain = "WORLD";
        int key = 5;
        String cipher = CaesarCipher.encrypt(plain, key);
        assertEquals("BTWQI", cipher);
    }

    @Test
    void testEncrypt_MixedAndSpecial() {
        String plain = "Hello, World! 123";
        int key = 7;
        String cipher = CaesarCipher.encrypt(plain, key);
        assertEquals("Olssv, Dvysk! 123", cipher);
    }

    @Test
    void testDecrypt_MixedAndSpecial() {
        String cipher = "Olssv, Dvysk! 123";
        int key = 7;
        String plain = CaesarCipher.decrypt(cipher, key);
        assertEquals("Hello, World! 123", plain);
    }

    @Test
    void testEncryptDecrypt_Roundtrip() {
        String original = "Java Programming 2026 @TCP";
        int key = 15;
        String cipher = CaesarCipher.encrypt(original, key);
        String decrypted = CaesarCipher.decrypt(cipher, key);
        assertEquals(original, decrypted);
    }

    @Test
    void testInvalidKey_ThrowsException() {
        Exception e1 = assertThrows(IllegalArgumentException.class, () -> CaesarCipher.encrypt("test", 0));
        assertTrue(e1.getMessage().contains("1 đến 25"));

        Exception e2 = assertThrows(IllegalArgumentException.class, () -> CaesarCipher.decrypt("test", 26));
        assertTrue(e2.getMessage().contains("1 đến 25"));
    }

}
