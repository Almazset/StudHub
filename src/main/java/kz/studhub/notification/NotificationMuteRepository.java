package kz.studhub.notification;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface NotificationMuteRepository extends JpaRepository<NotificationMute, Long> {
    List<NotificationMute> findByRecipientId(long recipient);
    boolean existsByRecipientIdAndType(long recipient, NotificationType type);

    @Query("select m.type from NotificationMute m where m.recipient.id = :recipient")
    List<NotificationType> mutedTypes(long recipient);
}
