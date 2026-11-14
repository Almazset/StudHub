package kz.studhub.repository;
import kz.studhub.model.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "course")
    List<Assignment> findByStudentId(Long studentId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "course")
    java.util.Optional<Assignment> findByIdAndStudentId(Long id, Long studentId);
}
