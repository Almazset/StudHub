package kz.studhub.repository;

import kz.studhub.model.SupportRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {
    List<SupportRequest> findTop20ByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    List<SupportRequest> findTop100ByOrderByCreatedAtDesc();
    long countByOwnerIdAndCreatedAtAfter(Long ownerId, Instant after);
}
