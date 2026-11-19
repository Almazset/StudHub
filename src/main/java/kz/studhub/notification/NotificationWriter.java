package kz.studhub.notification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class NotificationWriter {
    static final int MAX_HISTORY = 200;
    private static final int MAX_REMINDERS = 50;

    private final InboxRepository inbox;
    private final NotificationMuteRepository mutes;
    private final UserAccountRepository users;

    NotificationWriter(InboxRepository inbox, NotificationMuteRepository mutes, UserAccountRepository users) {
        this.inbox = inbox;
        this.mutes = mutes;
        this.users = users;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(NotificationService.Delivery event) {
        UserAccount recipient = users.findLockedById(event.recipient()).orElse(null);
        if (recipient == null || mutes.existsByRecipientIdAndType(event.recipient(), event.type())) return;

        var existing = inbox.findByRecipientIdAndTypeAndEventKey(event.recipient(), event.type(), event.key());
        if (existing.isPresent()) {
            InboxEntry entry = existing.get();
            if (event.type() == NotificationType.CHAT_MESSAGE && event.occurredAt().isAfter(entry.createdAt)) {
                entry.update(event.detail(), event.href(), event.occurredAt());
            }
            return;
        }
        inbox.save(new InboxEntry(recipient, event.type(), event.key(), event.detail(), event.href(), event.occurredAt()));
        trim(event.recipient());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void remind(long recipientId, List<NotificationService.Reminder> reminders) {
        UserAccount recipient = users.findLockedById(recipientId).orElse(null);
        if (recipient == null || mutes.existsByRecipientIdAndType(recipientId, NotificationType.DEADLINE)) return;

        Map<String, NotificationService.Reminder> pending = new LinkedHashMap<>();
        for (var reminder : reminders) {
            if (pending.size() == MAX_REMINDERS) break;
            pending.putIfAbsent(reminder.key(), reminder);
        }
        var existing = inbox.findByRecipientIdAndType(recipientId, NotificationType.DEADLINE);
        List<InboxEntry> stale = new ArrayList<>();
        for (InboxEntry entry : existing) {
            var current = pending.remove(entry.eventKey);
            if (current == null) {
                stale.add(entry);
            } else {
                // Updating the label must not reset acknowledgement or reorder the same reminder.
                entry.detail = current.detail();
                entry.href = current.href();
            }
        }
        if (!stale.isEmpty()) inbox.deleteAllInBatch(stale);

        Instant now = Instant.now();
        for (var reminder : pending.values()) {
            inbox.save(new InboxEntry(recipient, NotificationType.DEADLINE, reminder.key(), reminder.detail(), reminder.href(), now));
        }
        if (!pending.isEmpty()) trim(recipientId);
    }

    private void trim(long recipient) {
        long excess = inbox.countByRecipientId(recipient) - MAX_HISTORY;
        if (excess <= 0) return;
        var oldest = inbox.oldestEventIds(recipient, NotificationType.DEADLINE, PageRequest.of(0, (int)excess));
        inbox.deleteAllByIdInBatch(oldest);
    }
}
