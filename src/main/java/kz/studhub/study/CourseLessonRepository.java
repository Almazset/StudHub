package kz.studhub.study;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CourseLessonRepository extends JpaRepository<CourseLesson,Long>{
 List<CourseLesson> findByCourseIdOrderBySectionOrderAscLessonOrderAscIdAsc(Long courseId);
 Optional<CourseLesson> findByIdAndCourseId(Long id,Long courseId);
 long countByCourseId(Long courseId);
 void deleteByCourseId(Long courseId);
}
