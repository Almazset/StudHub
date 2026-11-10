package kz.studhub.service;

import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationServiceTest {
    @Test
    void registerEncodesPasswordAndCreatesUser() {
        UserAccountRepository repo = mock(UserAccountRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(repo.existsByEmailIgnoreCase("student@example.com")).thenReturn(false);
        when(encoder.encode("password123")).thenReturn("hashed");
        when(repo.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        RegistrationService service = new RegistrationService(repo, encoder);

        UserAccount result = service.register("Student Name", "student@example.com", "password123");

        assertEquals("student@example.com", result.getEmail());
        assertEquals("hashed", result.getPassword());
        verify(repo).save(any(UserAccount.class));
    }

    @Test
    void duplicateEmailIsRejected() {
        UserAccountRepository repo = mock(UserAccountRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(repo.existsByEmailIgnoreCase("student@example.com")).thenReturn(true);
        RegistrationService service = new RegistrationService(repo, encoder);
        assertThrows(IllegalArgumentException.class,
            () -> service.register("Student Name", "student@example.com", "password123"));
        verify(repo, never()).save(any());
    }
}
