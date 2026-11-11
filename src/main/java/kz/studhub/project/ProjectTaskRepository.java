package kz.studhub.project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectTaskRepository extends JpaRepository<ProjectTask,Long> {
 List<ProjectTask> findByProjectIdOrderByIdDesc(Long projectId);
 Optional<ProjectTask> findByIdAndProjectId(Long id, Long projectId);
}
