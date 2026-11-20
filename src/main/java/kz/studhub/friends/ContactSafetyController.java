package kz.studhub.friends;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/friends")
public class ContactSafetyController {
    private final ContactPrivacyService privacy;
    private final UserAccountRepository users;

    public ContactSafetyController(ContactPrivacyService privacy, UserAccountRepository users) {
        this.privacy = privacy;
        this.users = users;
    }

    public record ReportRequest(@NotNull UserReport.Reason reason, @Size(max = 1000) String details,
                                @Positive Long messageId) {}

    @GetMapping("/blocked")
    public ResponseEntity<Map<String, List<ContactPrivacyService.BlockedAccount>>> blocked(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(Map.of("users", privacy.blockedAccounts(me(principal))));
    }

    @PutMapping("/blocked/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(Principal principal, @PathVariable @Positive Long id,
                      @RequestParam(required = false) @Positive Long messageId) {
        privacy.block(me(principal), id, messageId);
    }

    @DeleteMapping("/blocked/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(Principal principal, @PathVariable @Positive Long id) {
        privacy.unblock(me(principal), id);
    }

    @PostMapping("/reports/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    public void report(Principal principal, @PathVariable @Positive Long id, @Valid @RequestBody ReportRequest input) {
        privacy.report(me(principal), id, input.reason(), input.details(), input.messageId());
    }

    @GetMapping("/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ContactPrivacyService.ReportView>> reports() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(privacy.recentReports());
    }

    private Long me(Principal principal) {
        return users.findByEmailIgnoreCase(principal.getName()).orElseThrow().getId();
    }
}
