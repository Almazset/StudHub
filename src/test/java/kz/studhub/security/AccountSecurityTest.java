package kz.studhub.security;

import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.core.userdetails.User;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountSecurityTest {
    @TempDir Path directory;
    @Test void totpMatchesRfc6238AndRejectsReplaysAndMalformedCodes() {
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertEquals("287082", Totp.code(secret, 59 / 30));
        assertEquals("081804", Totp.code(secret, 1111111109L / 30));
        assertEquals("050471", Totp.code(secret, 1111111111L / 30));
        assertEquals("005924", Totp.code(secret, 1234567890L / 30));
        assertEquals("279037", Totp.code(secret, 2000000000L / 30));
        assertEquals("353130", Totp.code(secret, 20000000000L / 30));
        assertEquals(1, Totp.matchingStep(secret,"287082",59,null));
        assertEquals(-1, Totp.matchingStep(secret,"287082",59,1L));
        assertEquals(-1, Totp.matchingStep(secret,"287082",180,null));
        assertEquals(-1, Totp.matchingStep(secret,"<code>",59,null));
    }
    @Test void encryptionSurvivesRestartAndRejectsTampering() {
        String path = directory.resolve("security.key").toString();
        var first = new SecretVault("",path);
        String ciphertext = first.encrypt("a private authenticator secret");
        assertFalse(ciphertext.contains("private"));
        assertEquals("a private authenticator secret",new SecretVault("",path).decrypt(ciphertext));
        byte[] tampered = Base64.getDecoder().decode(ciphertext); tampered[tampered.length-1] ^= 1;
        assertThrows(IllegalStateException.class,() -> first.decrypt(Base64.getEncoder().encodeToString(tampered)));
    }
    @Test void recoveryCodesAreSingleUseAndRepeatedInvalidCodesLockVerification() {
        var repository = mock(UserAccountRepository.class);
        var vault = new SecretVault("",directory.resolve("key").toString());
        var user = new UserAccount(); user.setEmail("owner@example.com");
        when(repository.findLockedByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var service = new TwoFactorService(repository,vault);
        var recovery = service.enable(user.getEmail(),Totp.newSecret(),Instant.now().getEpochSecond()/30);
        assertEquals(8,recovery.size());
        assertFalse(user.getRecoveryCodeHashes().contains(recovery.get(0)));
        assertTrue(service.verifyLogin(user.getEmail(),recovery.get(0)));
        assertFalse(service.verifyLogin(user.getEmail(),recovery.get(0)));
        for (int i=0;i<7;i++) assertFalse(service.verifyLogin(user.getEmail(),"invalid"));
        assertTrue(user.getOtpLockedUntil().isAfter(Instant.now()));
        assertFalse(service.verifyLogin(user.getEmail(),recovery.get(1)));
        user.setOtpLockedUntil(Instant.now().minusSeconds(1));
        assertTrue(service.verifyLogin(user.getEmail(),recovery.get(1)));
    }
    @Test void sessionsAreAccountScopedAndDoNotExposeAuthenticationCookies() {
        var registry = new DeviceSessionRegistry();
        var owner = User.withUsername("owner@example.com").password("hash").roles("USER").build();
        var other = User.withUsername("other@example.com").password("hash").roles("USER").build();
        registry.registerNewSession("owner-current-cookie",owner);
        registry.registerNewSession("owner-other-cookie",owner);
        registry.registerNewSession("other-cookie",other);
        var service = new AccountSessions(registry);
        var views = service.list(owner.getUsername(),"owner-current-cookie");
        assertEquals(2,views.size());
        assertTrue(views.get(0).current());
        assertFalse(views.toString().contains("cookie"));
        var reference = views.get(1).reference();
        assertFalse(service.revoke(other.getUsername(),reference,"other-cookie"));
        assertFalse(service.revoke(owner.getUsername(),views.get(0).reference(),"owner-current-cookie"));
        assertTrue(service.revoke(owner.getUsername(),reference,"owner-current-cookie"));
        assertEquals(1,service.list(owner.getUsername(),"owner-current-cookie").size());
        assertFalse(registry.getSessionInformation("other-cookie").isExpired());
    }
    @Test void accountSecretsAndPrivateProfilesCannotLeakThroughAssignmentSerialization() throws Exception {
        var user = new UserAccount(); user.setFullName("Private Name"); user.setEmail("private@example.com");
        user.setPassword("password-hash"); user.setTwoFactorSecret("secret-ciphertext"); user.setRecoveryCodeHashes("recovery-hash");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String serialized = mapper.writeValueAsString(user);
        assertFalse(serialized.contains("password-hash")); assertFalse(serialized.contains("secret-ciphertext")); assertFalse(serialized.contains("recovery-hash"));
        var assignment = new kz.studhub.model.Assignment(); assignment.setStudent(user);
        String task = mapper.writeValueAsString(assignment);
        assertFalse(task.contains("Private Name")); assertFalse(task.contains("private@example.com")); assertFalse(task.contains("student"));
    }
}
