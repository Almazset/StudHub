package kz.studhub.friends;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kz.studhub.chat.ChatService;
import kz.studhub.notification.NotificationService;
import kz.studhub.notification.NotificationType;
import kz.studhub.repository.UserAccountRepository;
import kz.studhub.repository.UserAccountRepository.ChatIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/friends")
public class FriendController {
    private final FriendshipRepository friends;
    private final UserAccountRepository users;
    private final ChatService chat;
    private final PresenceService presence;
    private final NotificationService notifications;
    private final ContactPrivacyService privacy;

    public FriendController(FriendshipRepository friends, UserAccountRepository users, ChatService chat,
                            PresenceService presence, NotificationService notifications, ContactPrivacyService privacy) {
        this.friends = friends;
        this.users = users;
        this.chat = chat;
        this.presence = presence;
        this.notifications = notifications;
        this.privacy = privacy;
    }

    public record Request(@NotNull @Positive Long userId) {}

    public record FriendView(Long id, Long userId, String name, String status, boolean incoming,
                             String presence, boolean profileVisible, boolean canMessage) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<FriendView> list(Principal principal) {
        Long viewer = userId(principal);
        var audience = privacy.audience(viewer);
        var relationships = friends.visible(viewer).stream()
            .filter(friendship -> !audience.blockedPeers().contains(peerOf(friendship, viewer))).toList();
        if (relationships.isEmpty()) return List.of();
        var identities = users.findChatIdentities(relationships.stream().map(f -> peerOf(f, viewer)).toList())
            .stream().collect(Collectors.toMap(ChatIdentity::getId, Function.identity()));
        var accepted = relationships.stream().filter(f -> "ACCEPTED".equals(f.status))
            .map(f -> peerOf(f, viewer)).toList();
        var statuses = presence.visibleStatuses(viewer, accepted);
        return relationships.stream().filter(f -> identities.containsKey(peerOf(f, viewer))).map(f -> {
            Long peer = peerOf(f, viewer);
            return view(f, viewer, audience, identities.get(peer), statuses.getOrDefault(peer, "HIDDEN"));
        }).toList();
    }

    @PostMapping
    @Transactional
    public FriendView send(Principal principal, @Valid @RequestBody Request request) {
        Long viewer = userId(principal);
        Long peer = request.userId();
        if (viewer.equals(peer)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        lockPair(viewer, peer);
        var target = users.findById(peer).filter(user -> privacy.canViewProfile(viewer, user))
            .orElseThrow(FriendController::notFound);
        Long low = Math.min(viewer, peer);
        Long high = Math.max(viewer, peer);
        if (friends.findByLowUserIdAndHighUserId(low, high).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заявка или дружба уже существует");
        }
        var friendship = new Friendship();
        friendship.lowUserId = low;
        friendship.highUserId = high;
        friendship.requesterId = viewer;
        friendship.status = "PENDING";
        friendship.createdAt = Instant.now();
        friends.saveAndFlush(friendship);
        notifications.publish(target.getId(), NotificationType.FRIEND_REQUEST, "friendship:" + friendship.id, "", "/friends");
        return view(friendship, viewer);
    }

    @PostMapping("/{id}/accept")
    @Transactional
    public FriendView accept(Principal principal, @PathVariable Long id) {
        Long viewer = userId(principal);
        var friendship = lockedFriendship(id, viewer);
        if (friendship.requesterId.equals(viewer) || !"PENDING".equals(friendship.status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        friendship.status = "ACCEPTED";
        return view(friends.saveAndFlush(friendship), viewer);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String,Boolean> remove(Principal principal, @PathVariable Long id) {
        friends.delete(lockedFriendship(id, userId(principal)));
        return Map.of("ok", true);
    }

    @PostMapping("/{id}/chat")
    @Transactional
    public ChatService.Room openChat(Principal principal, @PathVariable Long id) {
        Long viewer = userId(principal);
        var friendship = lockedFriendship(id, viewer);
        if (!"ACCEPTED".equals(friendship.status)) throw notFound();
        return chat.direct(principal.getName(), peerOf(friendship, viewer));
    }

    private Friendship lockedFriendship(Long id, Long viewer) {
        Long peer = friends.findPeer(id, viewer).orElseThrow(FriendController::notFound);
        lockPair(viewer, peer);
        if (privacy.isBlocked(viewer, peer)) throw notFound();
        return friends.findById(id).orElseThrow(FriendController::notFound);
    }

    private void lockPair(Long viewer, Long peer) {
        users.findLockedById(Math.min(viewer, peer)).orElseThrow(FriendController::notFound);
        users.findLockedById(Math.max(viewer, peer)).orElseThrow(FriendController::notFound);
    }

    private FriendView view(Friendship friendship, Long viewer) {
        var identity = users.findChatIdentities(List.of(peerOf(friendship, viewer))).stream().findFirst()
            .orElseThrow(FriendController::notFound);
        return view(friendship, viewer, privacy.audience(viewer), identity, "HIDDEN");
    }

    private FriendView view(Friendship friendship, Long viewer, ContactPrivacyService.Audience audience,
                            ChatIdentity identity, String status) {
        Long peer = identity.getId();
        boolean visible = audience.canViewProfile(peer, identity.getProfileVisibility());
        String name = visible ? displayName(identity) : "Участник";
        boolean canMessage = "ACCEPTED".equals(friendship.status) && audience.canSendDirect(peer, identity.getDirectMessages());
        return new FriendView(friendship.id, peer, name, friendship.status,
            !friendship.requesterId.equals(viewer), status, visible, canMessage);
    }

    private static String displayName(ChatIdentity identity) {
        String nickname = identity.getNickname();
        return nickname == null || nickname.isBlank() ? identity.getFullName() : nickname;
    }

    private static Long peerOf(Friendship friendship, Long viewer) {
        return viewer.equals(friendship.lowUserId) ? friendship.highUserId : friendship.lowUserId;
    }

    private Long userId(Principal principal) {
        return users.findByEmailIgnoreCase(principal.getName()).orElseThrow(FriendController::notFound).getId();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
