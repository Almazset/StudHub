package kz.studhub.config;

import kz.studhub.model.Role;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner createDemoAdmin(UserAccountRepository users, PasswordEncoder encoder,
            @Value("${studhub.admin.email}") String email,
            @Value("${studhub.admin.password}") String password) {
        return args -> {
            if (email.isBlank() && password.isBlank()) return;
            if (email.isBlank() || password.length() < 12) throw new IllegalStateException("Set both STUDHUB_ADMIN_EMAIL and a STUDHUB_ADMIN_PASSWORD of at least 12 characters");
            if (!users.existsByEmailIgnoreCase(email)) {
                UserAccount admin = new UserAccount();
                admin.setFullName("Алмаз");
                admin.setEmail(email.toLowerCase());
                admin.setPassword(encoder.encode(password));
                admin.setRole(Role.ADMIN);
                users.save(admin);
                LOG.info("Initial administrator created");
            }
        };
    }
}
