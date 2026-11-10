package kz.studhub.config;

import kz.studhub.model.Role;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner createDemoAdmin(UserAccountRepository users, PasswordEncoder encoder,
            @Value("${STUDHUB_ADMIN_EMAIL:admin@studhub.local}") String email,
            @Value("${STUDHUB_ADMIN_PASSWORD:ChangeMe123!}") String password) {
        return args -> {
            if (!users.existsByEmailIgnoreCase(email)) {
                UserAccount admin = new UserAccount();
                admin.setFullName("Алмаз");
                admin.setEmail(email.toLowerCase());
                admin.setPassword(encoder.encode(password));
                admin.setRole(Role.ADMIN);
                users.save(admin);
                System.out.println("Demo ADMIN created: " + email + " (change the default password before sharing/deployment)");
            }
        };
    }
}
