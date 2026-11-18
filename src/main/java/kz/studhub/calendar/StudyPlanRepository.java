package kz.studhub.calendar;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface StudyPlanRepository extends JpaRepository<StudyPlanItem, Long> {
    List<StudyPlanItem> findByOwnerIdAndDateBetweenOrderByDateAscIdAsc(long ownerId, LocalDate from, LocalDate to);
    Optional<StudyPlanItem> findByIdAndOwnerId(long id, long ownerId);
    long countByOwnerId(long ownerId);
}
