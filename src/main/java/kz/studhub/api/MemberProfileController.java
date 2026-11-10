package kz.studhub.api;

import java.util.Map;
import kz.studhub.model.*;
import kz.studhub.repository.UserAccountRepository;
import kz.studhub.service.AccountSettingsService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profiles")
public class MemberProfileController {
    private final UserAccountRepository users;
    private final AccountSettingsService settings;
    public MemberProfileController(UserAccountRepository users, AccountSettingsService settings) { this.users = users; this.settings = settings; }
    @GetMapping("/{id}") public ResponseEntity<?> profile(@PathVariable Long id, Authentication auth) {
        UserAccount user = visible(id, auth);
        return user == null ? ResponseEntity.notFound().build() : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            Map.of("id", user.getId(), "name", user.getFullName(), "hasPhoto", user.isHasPhoto()));
    }
    @GetMapping("/{id}/photo") public ResponseEntity<byte[]> photo(@PathVariable Long id, Authentication auth) {
        UserAccount user = visible(id, auth);
        return user == null || !user.isHasPhoto() ? ResponseEntity.notFound().build() : ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(user.getPhoto());
    }
    private UserAccount visible(Long id, Authentication auth) {
        var viewer = settings.account(auth.getName());
        return users.findById(id).filter(u -> u.getId().equals(viewer.getId()) || u.getProfileVisibility().equals("members")).orElse(null);
    }
}
