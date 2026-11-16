package kz.studhub.project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectEventRepository extends JpaRepository<ProjectEvent,Long> {
    List<ProjectEvent> findTop50ByProjectIdAndIdLessThanOrderByIdDesc(Long projectId,Long before);
}
