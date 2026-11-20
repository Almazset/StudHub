package kz.studhub.service;

import kz.studhub.model.Course;
import kz.studhub.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional
public class CourseService {
    private final CourseRepository courses;
    private final kz.studhub.study.CourseLessonRepository lessons;
    private final kz.studhub.study.LessonProgressRepository progress;
    private final kz.studhub.repository.AssignmentRepository assignments;
    private final kz.studhub.study.StudyStepRepository studySteps;
    public CourseService(CourseRepository courses, kz.studhub.study.CourseLessonRepository lessons,
                         kz.studhub.study.LessonProgressRepository progress,
                         kz.studhub.repository.AssignmentRepository assignments,
                         kz.studhub.study.StudyStepRepository studySteps) {
        this.courses = courses;
        this.lessons = lessons;
        this.progress = progress;
        this.assignments = assignments;
        this.studySteps = studySteps;
    }
    @Transactional(readOnly = true) public List<Course> findAll() { return courses.findAll(); }
    @Transactional(readOnly = true) public Course findById(Long id) { return courses.findById(id).orElseThrow(() -> new ResourceNotFoundException("Курс не найден: " + id)); }
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public Course save(Course course) { course.setId(null); return courses.save(course); }
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public Course update(Long id, Course form) { Course c = courses.findLockedById(id).orElseThrow(()->new ResourceNotFoundException("Курс не найден")); c.setTitle(form.getTitle()); c.setDescription(form.getDescription()); c.setTeacherName(form.getTeacherName()); c.setExternalUrl(form.getExternalUrl()); c.setStartInstructions(form.getStartInstructions()); c.setLanguage(form.getLanguage()); c.setAccessInfo(form.getAccessInfo()); return courses.save(c); }
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        var course = courses.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("Курс не найден"));
        if (assignments.existsByCourseId(id) || studySteps.existsByCourseId(id)) throw new CourseInUseException();
        progress.deleteByLessonCourseId(id);
        progress.flush();
        lessons.deleteByCourseId(id);
        lessons.flush();
        courses.delete(course);
    }
}
