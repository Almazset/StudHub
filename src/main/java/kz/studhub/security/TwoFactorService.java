package kz.studhub.security;

import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TwoFactorService {
    private final UserAccountRepository users;
    private final SecretVault vault;
    public TwoFactorService(UserAccountRepository users, SecretVault vault) { this.users = users; this.vault = vault; }

    @Transactional
    public boolean verifyLogin(String email, String code) {
        UserAccount user = users.findLockedByEmail(email).orElse(null);
        if (user == null) return false;
        if (!user.isTwoFactorEnabled()) return true;
        Instant now = Instant.now();
        if (user.getOtpLockedUntil() != null && now.isBefore(user.getOtpLockedUntil())) return false;
        String normalized = code == null ? "" : code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        long step = Totp.matchingStep(vault.decrypt(user.getTwoFactorSecret()), normalized, now.getEpochSecond(), user.getLastOtpStep());
        boolean valid = step >= 0;
        if (valid) user.setLastOtpStep(step);
        if (!valid && normalized.matches("[A-F0-9]{16}")) {
            List<String> hashes = new ArrayList<>(Arrays.asList(Optional.ofNullable(user.getRecoveryCodeHashes()).orElse("").split(",")));
            String candidate = hash(normalized);
            valid = hashes.removeIf(value -> MessageDigest.isEqual(value.getBytes(StandardCharsets.US_ASCII), candidate.getBytes(StandardCharsets.US_ASCII)));
            if (valid) user.setRecoveryCodeHashes(String.join(",", hashes));
        }
        if (valid) { user.setOtpFailures(0); user.setOtpLockedUntil(null); }
        else {
            int failures = user.getOtpFailures() + 1;
            user.setOtpFailures(failures >= 8 ? 0 : failures);
            if (failures >= 8) user.setOtpLockedUntil(now.plusSeconds(300));
        }
        users.save(user);
        return valid;
    }

    @Transactional
    public List<String> enable(String email, String secret, long verifiedStep) {
        UserAccount user = users.findLockedByEmail(email).orElseThrow();
        if (user.isTwoFactorEnabled()) throw new IllegalArgumentException("settings.error.alreadyEnabled");
        List<String> codes = new ArrayList<>();
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 8; i++) {
            byte[] bytes = new byte[8]; random.nextBytes(bytes);
            String raw = HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);
            codes.add(raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 12) + "-" + raw.substring(12));
        }
        user.setTwoFactorSecret(vault.encrypt(secret));
        user.setLastOtpStep(verifiedStep);
        user.setRecoveryCodeHashes(String.join(",", codes.stream().map(c -> hash(c.replace("-", ""))).toList()));
        user.setOtpFailures(0); user.setOtpLockedUntil(null);
        users.save(user);
        return codes;
    }

    @Transactional
    public void disable(String email) {
        UserAccount user = users.findLockedByEmail(email).orElseThrow();
        user.setTwoFactorSecret(null); user.setRecoveryCodeHashes(null); user.setLastOtpStep(null);
        user.setOtpFailures(0); user.setOtpLockedUntil(null); users.save(user);
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
