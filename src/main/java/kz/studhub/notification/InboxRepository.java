package kz.studhub.notification;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface InboxRepository extends JpaRepository<InboxEntry, Long> {
    Optional<InboxEntry> findByRecipientIdAndTypeAndEventKey(long recipient, NotificationType type, String eventKey);
    List<InboxEntry> findByRecipientIdAndType(long recipient, NotificationType type);
    List<InboxEntry> findTop50ByRecipientIdAndTypeInOrderByCreatedAtDescIdDesc(long recipient, Collection<NotificationType> types);
    long countByRecipientIdAndTypeInAndReadAtIsNull(long recipient, Collection<NotificationType> types);
    long countByRecipientId(long recipient);

    @Query("""
        select n.id from InboxEntry n
        where n.recipient.id = :recipient and n.type <> :preserved
        order by n.createdAt, n.id
        """)
    List<Long> oldestEventIds(long recipient, NotificationType preserved, Pageable limit);

    @Modifying
    @Query("""
        update InboxEntry n set n.readAt = coalesce(n.readAt, :now)
        where n.id = :id and n.recipient.id = :recipient
        """)
    int markRead(long recipient, long id, Instant now);

    @Modifying
    @Query("""
        update InboxEntry n set n.readAt = :now
        where n.recipient.id = :recipient and n.readAt is null
        """)
    int markAllRead(long recipient, Instant now);
}
