package kz.studhub.service;

import kz.studhub.model.Assignment;
import kz.studhub.model.AssignmentPriority;
import kz.studhub.repository.AssignmentRepository;
import kz.studhub.repository.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional
public class AssignmentService {
    private final AssignmentRepository assignments;
    private final CourseRepository courses;
    public AssignmentService(AssignmentRepository assignments, CourseRepository courses) { this.assignments = assignments; this.courses = courses; }
    @Transactional(readOnly = true) public List<Assignment> findAll() { return assignments.findAll(); }
    @Transactional(readOnly = true) public Assignment findById(Long id) { return assignments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Задание не найдено: " + id)); }
    public Assignment save(Assignment assignment, Long courseId) {
        if (assignment.getPriority() == null) assignment.setPriority(AssignmentPriority.MEDIUM);
        assignment.setCourse(courses.findById(courseId).orElseThrow(() -> new ResourceNotFoundException("Курс не найден: " + courseId)));
        return assignments.save(assignment);
    }
    public Assignment update(Long id, Assignment form, Long courseId) {
        Assignment a = findById(id);
        a.setTitle(form.getTitle()); a.setDescription(form.getDescription()); a.setDueDate(form.getDueDate()); a.setCompleted(form.isCompleted());
        a.setPriority(form.getPriority());
        a.setCourse(courses.findById(courseId).orElseThrow(() -> new ResourceNotFoundException("Курс не найден: " + courseId)));
        return assignments.save(a);
    }
    public void toggleCompleted(Long id) {
        Assignment assignment = findById(id);
        assignment.setCompleted(!assignment.isCompleted());
        assignments.save(assignment);
    }
    public void delete(Long id) { assignments.delete(findById(id)); }
}
