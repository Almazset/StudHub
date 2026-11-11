package kz.studhub.friends;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.Map;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/friends/presence")
public class PresenceController {
    private final PresenceService presence;
    private final UserAccountRepository users;
    public PresenceController(PresenceService presence,UserAccountRepository users) { this.presence=presence;this.users=users; }
    public record Choice(@NotBlank @Pattern(regexp="ONLINE|AWAY|BUSY|INVISIBLE") String status) {}
    private Long me(Principal principal) { return users.findByEmailIgnoreCase(principal.getName()).orElseThrow().getId(); }
    private ResponseEntity<?> response(String status) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("status",status)); }
    @GetMapping public ResponseEntity<?> current(Principal principal) { return response(presence.preference(me(principal))); }
    @PostMapping public ResponseEntity<?> heartbeat(Principal principal) {
        Long id=me(principal);presence.heartbeat(id);return response(presence.preference(id));
    }
    @PutMapping @Transactional public ResponseEntity<?> choose(Principal principal,@Valid @RequestBody Choice input) {
        // Serialize preference creation from multiple tabs of the same account.
        Long id=users.findLockedByEmail(principal.getName()).orElseThrow().getId();
        return response(presence.choose(id,input.status()));
    }
}
