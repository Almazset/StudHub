package kz.studhub.api;

import kz.studhub.model.Role;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class UserApiController {
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final kz.studhub.security.AccountSessions sessions;
    public UserApiController(UserAccountRepository users, PasswordEncoder encoder, kz.studhub.security.AccountSessions sessions) {
        this.users = users; this.encoder = encoder; this.sessions = sessions;
    }

    @GetMapping public List<UserView> all() { return users.findAll().stream().map(UserView::from).toList(); }
    @GetMapping("/{id}") public UserView one(@PathVariable Long id) { return UserView.from(find(id)); }
    @PostMapping public UserView create(@Valid @RequestBody UserRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) throw new IllegalArgumentException("Email уже используется");
        UserAccount u = new UserAccount(); u.setFullName(req.fullName()); u.setEmail(req.email().toLowerCase());
        u.setPassword(encoder.encode(req.password())); u.setRole(req.role() == null ? Role.USER : req.role());
        return UserView.from(users.save(u));
    }
    @PutMapping("/{id}") public UserView update(@PathVariable Long id, @Valid @RequestBody UserUpdate req) {
        UserAccount u = find(id);
        String previousEmail = u.getEmail();
        Role previousRole = u.getRole();
        users.findByEmailIgnoreCase(req.email()).filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new IllegalArgumentException("Email уже используется"); });
        u.setFullName(req.fullName()); u.setEmail(req.email().toLowerCase());
        if (req.role() != null) u.setRole(req.role());
        UserView saved = UserView.from(users.save(u));
        if (!previousEmail.equalsIgnoreCase(u.getEmail()) || previousRole != u.getRole())
            sessions.revokeAll(previousEmail);
        return saved;
    }
    @DeleteMapping("/{id}") public void delete(@PathVariable Long id) { var user = find(id); users.delete(user); sessions.revokeAll(user.getEmail()); }

    private UserAccount find(Long id) { return users.findById(id).orElseThrow(() -> new kz.studhub.service.ResourceNotFoundException("Пользователь не найден: " + id)); }
    public record UserRequest(@NotBlank @Size(min=2,max=80) String fullName, @NotBlank @Email @Size(max=120) String email,
      @NotBlank @Size(min=8,max=100) String password, Role role) {}
    public record UserUpdate(@NotBlank @Size(min=2,max=80) String fullName, @NotBlank @Email @Size(max=120) String email, Role role) {}
    public record UserView(Long id, String fullName, String email, Role role) {
        static UserView from(UserAccount u) { return new UserView(u.getId(), u.getFullName(), u.getEmail(), u.getRole()); }
    }
}
