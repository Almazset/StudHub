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
    public CourseService(CourseRepository courses) { this.courses = courses; }
    @Transactional(readOnly = true) public List<Course> findAll() { return courses.findAll(); }
    @Transactional(readOnly = true) public Course findById(Long id) { return courses.findById(id).orElseThrow(() -> new ResourceNotFoundException("Курс не найден: " + id)); }
    public Course save(Course course) { return courses.save(course); }
    public Course update(Long id, Course form) { Course c = findById(id); c.setTitle(form.getTitle()); c.setDescription(form.getDescription()); c.setTeacherName(form.getTeacherName()); c.setExternalUrl(form.getExternalUrl()); return courses.save(c); }
    public void delete(Long id) { courses.delete(findById(id)); }
}
