package kz.studhub.friends;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_reports", indexes = @Index(name = "user_reports_reporter_date_idx", columnList = "reporter_id,created_at"))
class UserReport {
    enum Reason { SPAM, HARASSMENT, INAPPROPRIATE, OTHER }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false)
    Long reporterId;

    @Column(nullable = false)
    Long reportedId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    Reason reason;

    @Column(nullable = false, length = 1000)
    String details;

    @Column(nullable = false)
    Instant createdAt;

    protected UserReport() {}

    UserReport(Long reporterId, Long reportedId, Reason reason, String details, Instant createdAt) {
        this.reporterId = reporterId;
        this.reportedId = reportedId;
        this.reason = reason;
        this.details = details;
        this.createdAt = createdAt;
    }
}
