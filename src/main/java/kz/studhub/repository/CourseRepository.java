package kz.studhub.repository;
import kz.studhub.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CourseRepository extends JpaRepository<Course, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Course c where c.id = :id")
    java.util.Optional<Course> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);}
