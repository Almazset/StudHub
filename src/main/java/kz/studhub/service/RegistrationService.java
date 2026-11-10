package kz.studhub.service;

import kz.studhub.model.Role;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    public RegistrationService(UserAccountRepository users, PasswordEncoder encoder) {
        this.users = users; this.encoder = encoder;
    }
    @Transactional
    public UserAccount register(String fullName, String email, String rawPassword) {
        if (users.existsByEmailIgnoreCase(email)) throw new IllegalArgumentException("Этот email уже зарегистрирован.");
        UserAccount user = new UserAccount();
        user.setFullName(fullName.trim());
        user.setEmail(email.trim().toLowerCase());
        user.setPassword(encoder.encode(rawPassword));
        user.setRole(Role.USER);
        return users.save(user);
    }
}
