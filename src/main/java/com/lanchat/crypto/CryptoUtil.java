package com.lanchat.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

public final class CryptoUtil {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Path KEY_FILE = Path.of(
            System.getProperty("user.home"), ".config", "lanchat", "chat.key");

    private static SecretKey key;

    private CryptoUtil() {}

    public static synchronized void init() throws IOException {
        Path dir = KEY_FILE.getParent();
        Files.createDirectories(dir);

        if (Files.exists(KEY_FILE)) {
            byte[] raw = Base64.getDecoder().decode(Files.readString(KEY_FILE).trim());
            if (raw.length != 32) throw new IOException("Key file corrupt");
            key = new SecretKeySpec(raw, "AES");
        } else {
            byte[] raw = new byte[32];
            RANDOM.nextBytes(raw);
            Files.writeString(KEY_FILE, Base64.getEncoder().encodeToString(raw));
            // chmod 600 on Linux/Arch
            try {
                Set<PosixFilePermission> perms = EnumSet.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
                Files.setPosixFilePermissions(KEY_FILE, perms);
            } catch (UnsupportedOperationException ignored) { /* Windows etc. */ }
        }
    }

    public record Encrypted(String ciphertextB64, String ivB64) {}

    public static Encrypted encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LEN];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new Encrypted(
                    Base64.getEncoder().encodeToString(ct),
                    Base64.getEncoder().encodeToString(iv));
        } catch (Exception e) {
            throw new RuntimeException("Encrypt failed", e);
        }
    }

    public static String decrypt(String ciphertextB64, String ivB64) {
        try {
            byte[] iv = Base64.getDecoder().decode(ivB64);
            byte[] ct = Base64.getDecoder().decode(ciphertextB64);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = c.doFinal(ct);
            return new String(pt, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "<decryption failed>";
        }
    }
}