package kz.studhub.service;

import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountSettingsService {
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    public AccountSettingsService(UserAccountRepository users, PasswordEncoder encoder) { this.users = users; this.encoder = encoder; }
    public UserAccount account(String email) { return users.findByEmailIgnoreCase(email).orElseThrow(() -> new ResourceNotFoundException("Account not found")); }
    public UserAccount lockedAccount(String email) { return users.findLockedByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Account not found")); }
    public void checkPassword(String email, String password) {
        if (password == null || !encoder.matches(password, account(email).getPassword())) throw new IllegalArgumentException("settings.error.password");
    }
    @Transactional
    public boolean updateProfile(String email, String fullName, String newEmail, String password) {
        return updateProfile(email, fullName, newEmail, password, null);
    }
    @Transactional
    public boolean updateProfile(String email, String fullName, String newEmail, String password, String nickname) {
        var user = users.findLockedByEmail(email).orElseThrow();
        String normalized = newEmail.trim().toLowerCase(Locale.ROOT);
        if (fullName.trim().length() < 2) throw new IllegalArgumentException("settings.error.validation");
        boolean changed = !user.getEmail().equalsIgnoreCase(normalized);
        if (changed) {
            checkPassword(email, password);
            users.findByEmailIgnoreCase(normalized).filter(other -> !other.getId().equals(user.getId())).ifPresent(other -> { throw new IllegalArgumentException("settings.error.emailUsed"); });
            user.setEmail(normalized);
        }
        if (nickname != null) {
            String trimmed = nickname.trim();
            if (trimmed.length() > 32 || trimmed.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("settings.error.validation");
            user.setNickname(trimmed.isEmpty() ? null : trimmed);
        }
        user.setFullName(fullName.trim()); users.save(user);
        return changed;
    }
    @Transactional
    public void changePassword(String email, String current, String next, String confirmation) {
        var user = users.findLockedByEmail(email).orElseThrow();
        checkPassword(email, current);
        if (!next.equals(confirmation)) throw new IllegalArgumentException("settings.error.passwordMismatch");
        if (next.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72 || next.length() < 8) throw new IllegalArgumentException("settings.error.passwordLength");
        if (encoder.matches(next, user.getPassword())) throw new IllegalArgumentException("settings.error.passwordSame");
        user.setPassword(encoder.encode(next)); users.save(user);
    }
    @Transactional
    public void appearance(String email, String theme, String fontSize, String language) {
        var user = users.findLockedByEmail(email).orElseThrow();
        user.setTheme(theme); user.setFontSize(fontSize); user.setLanguage(language); users.save(user);
    }
    @Transactional
    public void privacy(String email, String visibility, String directMessages, String presenceVisibility) {
        var user = users.findLockedByEmail(email).orElseThrow();
        if (visibility != null) user.setProfileVisibility(visibility);
        if (directMessages != null) user.setDirectMessages(directMessages);
        if (presenceVisibility != null) user.setPresenceVisibility(presenceVisibility);
    }
    @Transactional
    public void photo(String email, byte[] bytes) { var user = users.findLockedByEmail(email).orElseThrow(); user.setPhoto(bytes); users.save(user); }
}
