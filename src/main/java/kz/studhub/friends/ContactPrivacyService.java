package kz.studhub.friends;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import kz.studhub.chat.ChatMessageRepository;
import kz.studhub.chat.ChatRoomRepository;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ContactPrivacyService {
    private static final int DAILY_REPORT_LIMIT = 5;
    private final ContactBlockRepository blocks;
    private final UserReportRepository reports;
    private final FriendshipRepository friendships;
    private final UserAccountRepository users;
    private final ChatRoomRepository rooms;
    private final ChatMessageRepository messages;

    public ContactPrivacyService(ContactBlockRepository blocks, UserReportRepository reports,
                                 FriendshipRepository friendships, UserAccountRepository users,
                                 ChatRoomRepository rooms, ChatMessageRepository messages) {
        this.blocks = blocks;
        this.reports = reports;
        this.friendships = friendships;
        this.users = users;
        this.rooms = rooms;
        this.messages = messages;
    }

    public record Audience(Long viewer, Set<Long> friends, Set<Long> blockedPeers) {
        public boolean canViewProfile(Long target, String visibility) {
            return viewer.equals(target) || (!blockedPeers.contains(target)
                && ("members".equals(visibility) || ("friends".equals(visibility) && friends.contains(target))));
        }

        public boolean canSendDirect(Long target, String directMessages) {
            return !viewer.equals(target) && !blockedPeers.contains(target)
                && ("members".equals(directMessages) || ("friends".equals(directMessages) && friends.contains(target)));
        }
    }

    public record BlockedAccount(Long id, String publicId) {}

    public record ReportView(Long id, Long reporterId, Long reportedId, UserReport.Reason reason,
                             String details, Instant createdAt) {
        static ReportView of(UserReport row) {
            return new ReportView(row.id, row.reporterId, row.reportedId, row.reason, row.details, row.createdAt);
        }
    }

    public Audience audience(Long viewer) {
        return new Audience(viewer, friendPeers(viewer), blockedPeers(viewer));
    }

    public Set<Long> blockedPeers(Long viewer) {
        return Set.copyOf(blocks.peers(viewer));
    }

    public Set<Long> friendPeers(Long viewer) {
        return friendships.acceptedPeers(viewer);
    }

    public boolean isBlocked(Long first, Long second) {
        return !first.equals(second)
            && blocks.existsBetween(first, second);
    }

    public boolean areFriends(Long first, Long second) {
        return friendship(first, second).filter(friend -> "ACCEPTED".equals(friend.status)).isPresent();
    }

    public boolean canViewProfile(Long viewer, UserAccount target) {
        return canViewProfile(viewer, target.getId(), target.getProfileVisibility());
    }

    public boolean canViewProfile(Long viewer, Long target, String visibility) {
        return viewer.equals(target) || (("members".equals(visibility)
            || ("friends".equals(visibility) && areFriends(viewer, target))) && !isBlocked(viewer, target));
    }

    public boolean canSendDirect(Long sender, Long recipient) {
        if (sender.equals(recipient) || isBlocked(sender, recipient)) return false;
        return users.findById(recipient).map(UserAccount::getDirectMessages)
            .filter(rule -> "members".equals(rule) || ("friends".equals(rule) && areFriends(sender, recipient)))
            .isPresent();
    }

    public Set<Long> presencePeers(Long viewer, Collection<Long> requested) {
        if (requested.isEmpty()) return Set.of();
        var audience = audience(viewer);
        var eligible = requested.stream().filter(audience.friends()::contains)
            .filter(id -> !audience.blockedPeers().contains(id)).toList();
        return eligible.isEmpty() ? Set.of() : Set.copyOf(users.findIdsSharingPresence(eligible));
    }

    public List<BlockedAccount> blockedAccounts(Long viewer) {
        return blocks.findByBlockerIdOrderByCreatedAtDescIdDesc(viewer).stream()
            .map(row -> new BlockedAccount(row.blockedId, "SH-" + row.blockedId)).toList();
    }

    @Transactional
    public void block(Long viewer, Long target, Long messageId) {
        requireDifferent(viewer, target);
        lockPair(viewer, target);
        if (blocks.findByBlockerIdAndBlockedId(viewer, target).isPresent()) return;

        var relation = friendship(viewer, target);
        requireKnownTarget(viewer, target, relation, messageId);
        blocks.save(new ContactBlock(viewer, target));
        relation.ifPresent(friendships::delete);
    }

    @Transactional
    public void unblock(Long viewer, Long target) {
        requireDifferent(viewer, target);
        if (blocks.findByBlockerIdAndBlockedId(viewer, target).isEmpty()) return;
        lockPair(viewer, target);
        blocks.findByBlockerIdAndBlockedId(viewer, target).ifPresent(blocks::delete);
    }

    @Transactional
    public void report(Long viewer, Long target, UserReport.Reason reason, String details, Long messageId) {
        requireDifferent(viewer, target);
        lockPair(viewer, target);
        if (blocks.findByBlockerIdAndBlockedId(viewer, target).isEmpty()) {
            requireKnownTarget(viewer, target, friendship(viewer, target), messageId);
        }

        Instant now = Instant.now();
        String text = Objects.toString(details, "").strip();
        if (reports.existsByReporterIdAndReportedIdAndReasonAndDetailsAndCreatedAtAfter(
                viewer, target, reason, text, now.minus(Duration.ofMinutes(5)))) return;
        if (reports.countByReporterIdAndCreatedAtAfter(viewer, now.minus(Duration.ofDays(1))) >= DAILY_REPORT_LIMIT) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        }
        reports.save(new UserReport(viewer, target, reason, text, now));
    }

    public List<ReportView> recentReports() {
        return reports.findTop100ByOrderByCreatedAtDescIdDesc().stream().map(ReportView::of).toList();
    }

    private void requireKnownTarget(Long viewer, Long target, Optional<Friendship> relation, Long messageId) {
        var person = users.findById(target).orElseThrow(ContactPrivacyService::notFound);
        if (relation.isPresent() || canViewProfile(viewer, person)) return;
        if (rooms.findByDirectKey(Math.min(viewer, target) + ":" + Math.max(viewer, target)).isPresent()) return;
        if (messageId != null && messages.hasVisibleMessageFrom(viewer, target, messageId)) return;
        throw notFound();
    }

    private Optional<Friendship> friendship(Long first, Long second) {
        return friendships.findByLowUserIdAndHighUserId(Math.min(first, second), Math.max(first, second));
    }

    private void lockPair(Long first, Long second) {
        // Use the same lock order as direct chat. Locking the reporter also serializes the daily report limit.
        users.findLockedById(Math.min(first, second)).orElseThrow(ContactPrivacyService::notFound);
        users.findLockedById(Math.max(first, second)).orElseThrow(ContactPrivacyService::notFound);
    }

    private static void requireDifferent(Long first, Long second) {
        if (first.equals(second)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
