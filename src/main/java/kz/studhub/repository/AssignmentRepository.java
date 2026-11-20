package kz.studhub.repository;
import java.time.LocalDate;
import kz.studhub.model.Assignment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    boolean existsByCourseId(Long courseId);
    @EntityGraph(attributePaths = "course")
    List<Assignment> findByStudentId(Long studentId);
    @EntityGraph(attributePaths = "course")
    java.util.Optional<Assignment> findByIdAndStudentId(Long id, Long studentId);

    @EntityGraph(attributePaths = "course")
    List<Assignment> findByStudentIdAndDueDateBetween(Long studentId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = "course")
    @Query("""
        select a from Assignment a
        where a.student.id = :studentId and a.dueDate <= :through
          and (a.status <> kz.studhub.model.AssignmentStatus.DONE or (a.status is null and a.completed = false))
        order by case when a.dueDate = :today then 0 when a.dueDate < :today then 1 else 2 end,
                 case when a.dueDate < :today then a.dueDate else null end desc,
                 a.dueDate, case when a.course is null then 1 else 0 end, a.id
        """)
    List<Assignment> calendarFocus(Long studentId, LocalDate today, LocalDate through, Pageable limit);
}
