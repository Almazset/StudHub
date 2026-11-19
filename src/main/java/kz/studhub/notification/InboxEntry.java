package kz.studhub.notification;

import jakarta.persistence.*;
import java.time.Instant;
import kz.studhub.model.UserAccount;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "user_notifications",
    uniqueConstraints = @UniqueConstraint(name = "user_notification_dedupe", columnNames = {"recipient_id", "type", "event_key"}),
    indexes = @Index(name = "user_notification_recent", columnList = "recipient_id, created_at, id"))
class InboxEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    UserAccount recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    NotificationType type;

    @Column(nullable = false, length = 160)
    String eventKey;

    @Column(nullable = false, length = 240)
    String detail;

    @Column(nullable = false, length = 240)
    String href;

    @Column(nullable = false)
    Instant createdAt;

    Instant readAt;

    protected InboxEntry() {}

    InboxEntry(UserAccount recipient, NotificationType type, String eventKey, String detail, String href, Instant occurredAt) {
        this.recipient = recipient;
        this.type = type;
        this.eventKey = eventKey;
        update(detail, href, occurredAt);
    }

    void update(String detail, String href, Instant occurredAt) {
        this.detail = detail;
        this.href = href;
        this.createdAt = occurredAt;
        this.readAt = null;
    }
}
