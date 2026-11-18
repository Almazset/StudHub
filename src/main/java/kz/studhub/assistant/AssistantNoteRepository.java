package kz.studhub.assistant;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssistantNoteRepository extends JpaRepository<AssistantNote, Long> {
    interface Summary {
        Long getId();
        String getTitle();
        Instant getCreatedAt();
    }

    List<Summary> findByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);
    Optional<AssistantNote> findByIdAndOwnerId(Long id, Long ownerId);
    long countByOwnerId(Long ownerId);
}
