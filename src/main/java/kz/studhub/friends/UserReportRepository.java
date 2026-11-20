package kz.studhub.friends;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserReportRepository extends JpaRepository<UserReport, Long> {
    long countByReporterIdAndCreatedAtAfter(Long reporterId, Instant since);
    boolean existsByReporterIdAndReportedIdAndReasonAndDetailsAndCreatedAtAfter(
        Long reporterId, Long reportedId, UserReport.Reason reason, String details, Instant since);
    List<UserReport> findTop100ByOrderByCreatedAtDescIdDesc();
}
