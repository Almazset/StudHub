package kz.studhub.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class AccountSessions {
    private final DeviceSessionRegistry registry;
    public AccountSessions(DeviceSessionRegistry registry) { this.registry = registry; }
    public record View(String reference, String device, Instant createdAt, Instant lastActive, boolean current) {}
    private List<SessionInformation> sessions(String email) {
        return registry.getAllPrincipals().stream().filter(p -> p instanceof UserDetails u && u.getUsername().equalsIgnoreCase(email))
                .flatMap(p -> registry.getAllSessions(p, false).stream()).toList();
    }
    public List<View> list(String email, String currentId) {
        return sessions(email).stream().map(s -> {
            var device = registry.device(s.getSessionId());
            return new View(reference(s.getSessionId()), device.label(), device.createdAt(), s.getLastRequest().toInstant(), s.getSessionId().equals(currentId));
        }).sorted(Comparator.comparing(View::current).reversed().thenComparing(View::lastActive, Comparator.reverseOrder())).toList();
    }
    public boolean revoke(String email, String reference, String currentId) {
        var session = sessions(email).stream().filter(s -> reference(s.getSessionId()).equals(reference) && !s.getSessionId().equals(currentId)).findFirst();
        session.ifPresent(SessionInformation::expireNow);
        return session.isPresent();
    }
    public void revokeOthers(String email, String currentId) { sessions(email).stream().filter(s -> !s.getSessionId().equals(currentId)).forEach(SessionInformation::expireNow); }
    public void revokeAll(String email) { sessions(email).forEach(SessionInformation::expireNow); }
    private String reference(String sessionId) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8))).substring(0, 32); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
