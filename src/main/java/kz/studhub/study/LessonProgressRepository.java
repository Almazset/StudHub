package kz.studhub.study;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LessonProgressRepository extends JpaRepository<LessonProgress,Long>{
 List<LessonProgress> findByUserIdAndLessonCourseId(Long userId,Long courseId);
 Optional<LessonProgress> findByUserIdAndLessonId(Long userId,Long lessonId);
 void deleteByLessonId(Long lessonId);
 void deleteByLessonCourseId(Long courseId);
}
