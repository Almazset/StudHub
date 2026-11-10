package kz.studhub.service;

import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountSettingsServiceTest {
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AccountSettingsService service = new AccountSettingsService(users,encoder);
    private UserAccount user;
    @BeforeEach void setup() {
        user = new UserAccount(); user.setId(1L); user.setFullName("Owner"); user.setEmail("owner@example.com"); user.setPassword(encoder.encode("oldpassword"));
        when(users.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(user));
        when(users.findLockedByEmail("owner@example.com")).thenReturn(Optional.of(user));
    }
    @Test void changingEmailRequiresCurrentPasswordAndRejectsDuplicateAddresses() {
        assertThrows(IllegalArgumentException.class,() -> service.updateProfile(user.getEmail(),"Owner","next@example.com","wrong"));
        assertEquals("owner@example.com",user.getEmail());
        var other = new UserAccount(); other.setId(2L);
        when(users.findByEmailIgnoreCase("next@example.com")).thenReturn(Optional.of(other));
        assertThrows(IllegalArgumentException.class,() -> service.updateProfile(user.getEmail(),"Owner","next@example.com","oldpassword"));
        assertEquals("owner@example.com",user.getEmail());
        verify(users,never()).save(any());
    }
    @Test void emailAndNameAreNormalizedWithoutChangingRole() {
        assertTrue(service.updateProfile(user.getEmail(),"  New Name  ","  NEW@Example.com  ","oldpassword"));
        assertEquals("New Name",user.getFullName()); assertEquals("new@example.com",user.getEmail());
        assertEquals(kz.studhub.model.Role.USER,user.getRole()); verify(users).save(user);
    }
    @Test void passwordChangeVerifiesConfirmationAndStoresOnlyHash() {
        assertThrows(IllegalArgumentException.class,() -> service.changePassword(user.getEmail(),"oldpassword","newpassword","different"));
        assertThrows(IllegalArgumentException.class,() -> service.changePassword(user.getEmail(),"oldpassword","oldpassword","oldpassword"));
        service.changePassword(user.getEmail(),"oldpassword","newpassword","newpassword");
        assertTrue(encoder.matches("newpassword",user.getPassword())); assertFalse(user.getPassword().equals("newpassword"));
    }
    @Test void passwordLimitUsesBytesToAvoidSilentBcryptTruncation() {
        String tooLong = "😀".repeat(20);
        assertThrows(IllegalArgumentException.class,() -> service.changePassword(user.getEmail(),"oldpassword",tooLong,tooLong));
        verify(users,never()).save(any());
    }
}
