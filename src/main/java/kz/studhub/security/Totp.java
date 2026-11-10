package kz.studhub.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238, SHA-1, six digits, 30 second steps. */
public final class Totp {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private Totp() {}

    public static String newSecret() {
        byte[] bytes = new byte[20];
        new SecureRandom().nextBytes(bytes);
        StringBuilder result = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 255);
            bits += 8;
            while (bits >= 5) { bits -= 5; result.append(ALPHABET.charAt((buffer >>> bits) & 31)); }
        }
        return result.toString();
    }

    public static String code(String secret, long step) {
        try {
            byte[] key = new byte[secret.length() * 5 / 8];
            int buffer = 0, bits = 0, index = 0;
            for (char c : secret.toCharArray()) {
                int value = ALPHABET.indexOf(c);
                if (value < 0) throw new IllegalArgumentException("Invalid authenticator secret");
                buffer = (buffer << 5) | value; bits += 5;
                if (bits >= 8) { bits -= 8; key[index++] = (byte) (buffer >>> bits); }
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = digest[digest.length - 1] & 15;
            int number = ByteBuffer.wrap(digest, offset, 4).getInt() & 0x7fffffff;
            return String.format(java.util.Locale.ROOT, "%06d", number % 1_000_000);
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }

    public static long matchingStep(String secret, String candidate, long epochSeconds, Long usedStep) {
        if (candidate == null || !candidate.matches("[0-9]{6}")) return -1;
        long current = epochSeconds / 30;
        for (long step = current - 1; step <= current + 1; step++) {
            if (step >= 0 && (usedStep == null || step > usedStep)
                    && MessageDigest.isEqual(code(secret, step).getBytes(StandardCharsets.US_ASCII),
                    candidate.getBytes(StandardCharsets.US_ASCII))) return step;
        }
        return -1;
    }
}
