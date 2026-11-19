package kz.studhub.notification;

import jakarta.persistence.*;
import kz.studhub.model.UserAccount;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "notification_mutes",
    uniqueConstraints = @UniqueConstraint(name = "notification_mute_once", columnNames = {"recipient_id", "type"}))
class NotificationMute {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    UserAccount recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    NotificationType type;

    protected NotificationMute() {}

    NotificationMute(UserAccount recipient, NotificationType type) {
        this.recipient = recipient;
        this.type = type;
    }
}
