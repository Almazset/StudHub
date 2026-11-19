package kz.studhub.notification;

import java.net.URI;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);

    private final ApplicationEventPublisher events;
    private final NotificationWriter writer;
    private final InboxRepository inbox;
    private final NotificationMuteRepository mutes;
    private final UserAccountRepository users;

    public record Reminder(String key, String detail, String href) {}
    public record Item(Long id, NotificationType type, String detail, String href, Instant createdAt, boolean read) {}
    public record Feed(long unread, List<Item> items) {}
    record Delivery(long recipient, NotificationType type, String key, String detail, String href, Instant occurredAt) {}

    NotificationService(ApplicationEventPublisher events, NotificationWriter writer, InboxRepository inbox,
                        NotificationMuteRepository mutes, UserAccountRepository users) {
        this.events = events;
        this.writer = writer;
        this.inbox = inbox;
        this.mutes = mutes;
        this.users = users;
    }

    public void publish(long recipient, NotificationType type, String key, String detail, String href) {
        if (recipient < 1 || type == null || type == NotificationType.DEADLINE || !validKey(key) || !validHref(href)) return;
        events.publishEvent(new Delivery(recipient, type, key, clean(detail), href, Instant.now()));
    }

    @TransactionalEventListener(fallbackExecution = true)
    void afterCommit(Delivery event) {
        try {
            writer.deliver(event);
        } catch (RuntimeException error) {
            // The source action is already committed. Do not expose data or fail its response.
            LOG.warn("Notification delivery failed: {} ({})", event.type(), error.getClass().getSimpleName());
        }
    }

    public void remind(long recipient, List<Reminder> reminders) {
        writer.remind(recipient, reminders.stream()
            .filter(reminder -> validKey(reminder.key()) && validHref(reminder.href()))
            .map(reminder -> new Reminder(reminder.key(), clean(reminder.detail()), reminder.href()))
            .limit(50).toList());
    }

    public long recipientId(String email) {
        return users.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).getId();
    }

    @Transactional(readOnly = true)
    public Feed feed(long recipient) {
        Set<NotificationType> visible = EnumSet.allOf(NotificationType.class);
        visible.removeAll(mutes.mutedTypes(recipient));
        if (visible.isEmpty()) return new Feed(0, List.of());

        long unread = inbox.countByRecipientIdAndTypeInAndReadAtIsNull(recipient, visible);
        var items = inbox.findTop50ByRecipientIdAndTypeInOrderByCreatedAtDescIdDesc(recipient, visible).stream()
            .map(entry -> new Item(entry.id, entry.type, entry.detail, entry.href, entry.createdAt, entry.readAt != null))
            .toList();
        return new Feed(unread, items);
    }

    @Transactional
    public void markRead(long recipient, long id) {
        if (inbox.markRead(recipient, id, Instant.now()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @Transactional
    public void markAllRead(long recipient) {
        inbox.markAllRead(recipient, Instant.now());
    }

    @Transactional(readOnly = true)
    public Map<NotificationType, Boolean> preferences(long recipient) {
        Set<NotificationType> enabled = EnumSet.allOf(NotificationType.class);
        enabled.removeAll(mutes.mutedTypes(recipient));
        var result = new EnumMap<NotificationType, Boolean>(NotificationType.class);
        for (var type : NotificationType.values()) result.put(type, enabled.contains(type));
        return result;
    }

    @Transactional
    public Map<NotificationType, Boolean> savePreferences(long recipient, Map<NotificationType, Boolean> requested) {
        UserAccount owner = users.findLockedById(recipient)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Set<NotificationType> alreadyMuted = EnumSet.noneOf(NotificationType.class);
        for (var mute : mutes.findByRecipientId(recipient)) {
            alreadyMuted.add(mute.type);
            if (requested.get(mute.type)) mutes.delete(mute);
        }
        for (var type : NotificationType.values()) {
            if (!requested.get(type) && !alreadyMuted.contains(type)) mutes.save(new NotificationMute(owner, type));
        }
        return requested;
    }

    private static boolean validKey(String key) {
        return key != null && !key.isBlank() && key.length() <= 160 && key.codePoints().noneMatch(Character::isISOControl);
    }

    private static boolean validHref(String href) {
        if (href == null || href.length() > 240 || !href.startsWith("/") || href.startsWith("//") || href.contains("\\")) return false;
        try {
            var uri = URI.create(href);
            return uri.getAuthority() == null &&
                uri.getPath().matches("/(?:chat|friends|calendar|projects(?:/[0-9]+)?|assignments(?:/[0-9]+/edit)?)");
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static String clean(String detail) {
        if (detail == null) return "";
        StringBuilder text = new StringBuilder();
        detail.strip().codePoints().filter(point -> !Character.isISOControl(point)).limit(120).forEach(text::appendCodePoint);
        return text.toString();
    }
}
