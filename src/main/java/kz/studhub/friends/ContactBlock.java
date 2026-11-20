package kz.studhub.friends;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_blocks", uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"}),
       indexes = @Index(name = "user_blocks_blocked_idx", columnList = "blocked_id"))
class ContactBlock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "blocker_id", nullable = false)
    Long blockerId;

    @Column(name = "blocked_id", nullable = false)
    Long blockedId;

    @Column(nullable = false)
    Instant createdAt;

    protected ContactBlock() {}

    ContactBlock(Long blockerId, Long blockedId) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
        this.createdAt = Instant.now();
    }
}
