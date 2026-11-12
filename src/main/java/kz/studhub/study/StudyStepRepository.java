package kz.studhub.study;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface StudyStepRepository extends JpaRepository<StudyStep,Long> {
 List<StudyStep> findByUserIdAndCourseIdOrderByIdAsc(Long userId,Long courseId);
 Optional<StudyStep> findByIdAndUserIdAndCourseId(Long id,Long userId,Long courseId);
 long countByUserIdAndCourseId(Long userId,Long courseId);
}
