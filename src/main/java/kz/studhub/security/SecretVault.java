package kz.studhub.security;

import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecretVault {
    private final SecretKeySpec key;

    public SecretVault(@Value("${studhub.security.key:}") String configuredKey,
                       @Value("${studhub.security.key-file:.studhub/security.key}") String keyFile) {
        try {
            byte[] bytes;
            if (!configuredKey.isBlank()) bytes = Base64.getDecoder().decode(configuredKey);
            else {
                Path file = Path.of(keyFile).toAbsolutePath().normalize();
                Files.createDirectories(file.getParent());
                if (!Files.exists(file)) {
                    byte[] generated = new byte[32]; new SecureRandom().nextBytes(generated);
                    try {
                        if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                            Files.createFile(file, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
                            Files.write(file, generated);
                        } else Files.write(file, generated, StandardOpenOption.CREATE_NEW);
                    } catch (FileAlreadyExistsException ignored) { /* Another local instance created the key. */ }
                }
                bytes = Files.readAllBytes(file);
            }
            if (bytes.length != 32) throw new IllegalArgumentException("A 32-byte encryption key is required");
            key = new SecretKeySpec(bytes, "AES");
        } catch (Exception ex) { throw new IllegalStateException("Cannot initialize the authenticator secret store", ex); }
    }

    public String encrypt(String value) {
        try {
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(java.nio.ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception ex) { throw new IllegalStateException("Cannot protect authenticator secret", ex); }
    }

    public String decrypt(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, bytes, 0, 12));
            return new String(cipher.doFinal(bytes, 12, bytes.length - 12), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("Cannot read authenticator secret", ex); }
    }
}
