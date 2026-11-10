package kz.studhub.api;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import kz.studhub.model.*;
import kz.studhub.repository.*;
import kz.studhub.security.*;
import kz.studhub.service.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/settings")
public class SettingsApiController {
    private final AccountSettingsService settings;
    private final TwoFactorService twoFactor;
    private final AccountSessions sessions;
    private final ProfilePhotoService photos;
    private final SupportRequestRepository support;
    private final String version;
    public SettingsApiController(AccountSettingsService settings, TwoFactorService twoFactor, AccountSessions sessions,
                                 ProfilePhotoService photos, SupportRequestRepository support,
                                 @Value("${spring.application.version:0.0.1}") String version) {
        this.settings = settings; this.twoFactor = twoFactor; this.sessions = sessions;
        this.photos = photos; this.support = support; this.version = version;
    }
    public record Appearance(@NotNull @Pattern(regexp="light|dark") String theme,
                             @NotNull @Pattern(regexp="small|normal|large") String fontSize,
                             @NotNull @Pattern(regexp="ru|kk|en") String language) {}
    public record Profile(@NotBlank @Size(min=2,max=80) String name, @NotBlank @Email @Size(max=120) String email,
                          @Size(max=100) String currentPassword, @Size(max=32) String code, @Size(max=32) String nickname) {}
    public record PasswordChange(@NotBlank @Size(max=100) String currentPassword,
                                 @NotBlank @Size(min=8,max=72) String newPassword,
                                 @NotBlank @Size(max=72) String confirmPassword, @Size(max=32) String code) {}
    public record Proof(@NotBlank @Size(max=100) String password, @Size(max=32) String code) {}
    public record Visibility(@NotNull @Pattern(regexp="private|members") String visibility) {}
    public record Code(@NotBlank @Pattern(regexp="[0-9]{6}") String code) {}
    public record Ticket(@NotBlank @Size(min=3,max=120) String subject, @NotBlank @Size(min=10,max=2000) String message) {}
    private record Enrollment(String secret, Instant expires, int attempts) {}
    private static final String ENROLLMENT = "studhub.2fa.enrollment";
    @ModelAttribute public void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    @GetMapping
    public ResponseEntity<?> get(Authentication auth, HttpSession session) {
        var user = settings.account(auth.getName());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
            "profile", Map.of("id", user.getId(), "name", user.getFullName(), "nickname", user.getNickname() == null ? "" : user.getNickname(), "email", user.getEmail(), "hasPhoto", user.isHasPhoto(), "admin", user.getRole() == Role.ADMIN),
            "appearance", new Appearance(user.getTheme(), user.getFontSize(), user.getLanguage()),
            "privacy", Map.of("visibility", user.getProfileVisibility()),
            "security", Map.of("twoFactorEnabled", user.isTwoFactorEnabled()),
            "sessions", sessions.list(auth.getName(), session.getId()), "version", version,
            "support", support.findTop20ByOwnerIdOrderByCreatedAtDesc(user.getId()).stream().map(this::ticketView).toList()));
    }
    @PutMapping("/appearance")
    public Map<String, Boolean> appearance(Authentication auth, @Valid @RequestBody Appearance input) {
        settings.appearance(auth.getName(), input.theme(), input.fontSize(), input.language()); return Map.of("saved", true);
    }
    @PutMapping("/profile")
    public Map<String, Boolean> profile(Authentication auth, HttpSession session, @Valid @RequestBody Profile input) {
        var user = settings.account(auth.getName());
        if (!user.getEmail().equalsIgnoreCase(input.email().trim())) {
            settings.checkPassword(auth.getName(), input.currentPassword()); requireFactor(auth, input.code());
        }
        boolean changed = settings.updateProfile(auth.getName(), input.name(), input.email(), input.currentPassword(), input.nickname());
        if (changed) { sessions.revokeAll(auth.getName()); session.invalidate(); SecurityContextHolder.clearContext(); }
        return Map.of("saved", true, "reauthenticate", changed);
    }
    @PostMapping("/password")
    public Map<String, Boolean> password(Authentication auth, HttpSession session, @Valid @RequestBody PasswordChange input) {
        settings.checkPassword(auth.getName(), input.currentPassword()); requireFactor(auth, input.code());
        settings.changePassword(auth.getName(), input.currentPassword(), input.newPassword(), input.confirmPassword());
        sessions.revokeOthers(auth.getName(), session.getId()); return Map.of("saved", true);
    }
    @PutMapping("/privacy")
    public Map<String, Boolean> privacy(Authentication auth, @Valid @RequestBody Visibility input) {
        settings.visibility(auth.getName(), input.visibility()); return Map.of("saved", true);
    }
    @PostMapping(value="/photo", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Boolean> photo(Authentication auth, @RequestParam MultipartFile photo) {
        settings.photo(auth.getName(), photos.normalize(photo)); return Map.of("saved", true);
    }
    @DeleteMapping("/photo") public Map<String, Boolean> removePhoto(Authentication auth) { settings.photo(auth.getName(), null); return Map.of("saved", true); }
    @GetMapping("/photo") public ResponseEntity<byte[]> photo(Authentication auth) {
        byte[] photo = settings.account(auth.getName()).getPhoto();
        return photo == null ? ResponseEntity.notFound().build() : ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(photo);
    }
    @PostMapping("/two-factor/start")
    public ResponseEntity<?> start(Authentication auth, HttpSession session, @Valid @RequestBody Proof input) {
        settings.checkPassword(auth.getName(), input.password());
        if (settings.account(auth.getName()).isTwoFactorEnabled()) throw new IllegalArgumentException("settings.error.alreadyEnabled");
        String secret = Totp.newSecret();
        session.setAttribute(ENROLLMENT, new Enrollment(secret, Instant.now().plusSeconds(300), 0));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("secret", secret));
    }
    @PostMapping("/two-factor/enable")
    public ResponseEntity<?> enable(Authentication auth, HttpSession session, @Valid @RequestBody Code input) {
        Enrollment enrollment = (Enrollment) session.getAttribute(ENROLLMENT);
        if (enrollment == null || Instant.now().isAfter(enrollment.expires()) || enrollment.attempts() >= 8) {
            session.removeAttribute(ENROLLMENT); throw new IllegalArgumentException("settings.error.enrollmentExpired");
        }
        session.setAttribute(ENROLLMENT, new Enrollment(enrollment.secret(), enrollment.expires(), enrollment.attempts() + 1));
        long step = Totp.matchingStep(enrollment.secret(), input.code(), Instant.now().getEpochSecond(), null);
        if (step < 0) throw new IllegalArgumentException("settings.error.code");
        var recovery = twoFactor.enable(auth.getName(), enrollment.secret(), step);
        session.removeAttribute(ENROLLMENT); sessions.revokeOthers(auth.getName(), session.getId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("recoveryCodes", recovery));
    }
    @DeleteMapping("/two-factor")
    public Map<String, Boolean> disable(Authentication auth, HttpSession session, @Valid @RequestBody Proof input) {
        settings.checkPassword(auth.getName(), input.password()); requireFactor(auth, input.code());
        twoFactor.disable(auth.getName()); sessions.revokeOthers(auth.getName(), session.getId()); return Map.of("saved", true);
    }
    @DeleteMapping("/sessions/{reference}")
    public Map<String, Boolean> revoke(Authentication auth, HttpSession session, @PathVariable String reference) {
        if (!sessions.revoke(auth.getName(), reference, session.getId())) throw new ResourceNotFoundException("settings.error.session");
        return Map.of("saved", true);
    }
    @DeleteMapping("/sessions") public Map<String, Boolean> revokeOthers(Authentication auth, HttpSession session) {
        sessions.revokeOthers(auth.getName(), session.getId()); return Map.of("saved", true);
    }
    @PostMapping("/support")
    public Map<String, Object> support(Authentication auth, @Valid @RequestBody Ticket input) {
        var user = settings.account(auth.getName());
        if (input.subject().trim().length() < 3 || input.message().trim().length() < 10) throw new IllegalArgumentException("settings.error.validation");
        if (support.countByOwnerIdAndCreatedAtAfter(user.getId(), Instant.now().minusSeconds(86400)) >= 5) throw new IllegalArgumentException("settings.error.supportLimit");
        var ticket = new SupportRequest(); ticket.setOwnerId(user.getId()); ticket.setSubject(input.subject().trim()); ticket.setMessage(input.message().trim());
        return ticketView(support.save(ticket));
    }
    @GetMapping("/support/inbox") @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> inbox() { return support.findTop100ByOrderByCreatedAtDesc().stream().map(this::ticketView).toList(); }
    @PostMapping("/support/{id}/close") @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Boolean> close(@PathVariable Long id) {
        var ticket = support.findById(id).orElseThrow(() -> new ResourceNotFoundException("Not found"));
        ticket.setClosed(true); support.save(ticket); return Map.of("saved", true);
    }
    private void requireFactor(Authentication auth, String code) {
        if (!twoFactor.verifyLogin(auth.getName(), code)) throw new IllegalArgumentException("settings.error.code");
    }
    private Map<String, Object> ticketView(SupportRequest ticket) {
        return Map.of("id", ticket.getId(), "ownerId", ticket.getOwnerId(), "subject", ticket.getSubject(), "message", ticket.getMessage(), "createdAt", ticket.getCreatedAt(), "closed", ticket.isClosed());
    }
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> badInput(IllegalArgumentException ex) {
        String key = ex.getMessage() != null && ex.getMessage().startsWith("settings.error.") ? ex.getMessage() : "settings.error.validation";
        return ResponseEntity.badRequest().body(Map.of("error", key));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> invalid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "settings.error.validation"));
    }
}
