package kz.studhub.service;

import kz.studhub.model.Assignment;
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
    private final kz.studhub.repository.UserAccountRepository users;
    public AssignmentService(AssignmentRepository assignments, CourseRepository courses, kz.studhub.repository.UserAccountRepository users) {
        this.assignments = assignments; this.courses = courses; this.users = users;
    }
    // All callers, including dashboards and APIs, share this ownership boundary.
    private kz.studhub.model.UserAccount currentOwner() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
            throw new org.springframework.security.access.AccessDeniedException("Требуется вход");
        return users.findByEmailIgnoreCase(auth.getName()).orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Аккаунт не найден"));
    }
    @Transactional(readOnly = true) public List<Assignment> findAll() { return assignments.findByStudentId(currentOwner().getId()); }
    @Transactional(readOnly = true) public Assignment findById(Long id) { return assignments.findByIdAndStudentId(id, currentOwner().getId()).orElseThrow(() -> new ResourceNotFoundException("Задание не найдено: " + id)); }
    public Assignment save(Assignment form, Long courseId) {
        Assignment assignment = new Assignment();
        assignment.setStudent(currentOwner());
        assignment.setTitle(form.getTitle()); assignment.setDescription(form.getDescription());
        assignment.setDueDate(form.getDueDate()); assignment.setStatus(form.getStatus());
        assignment.setPriority(form.getPriority());
        assignment.setCourse(courseId == null ? null : courses.findById(courseId).orElseThrow(() -> new ResourceNotFoundException("Курс не найден: " + courseId)));
        return assignments.save(assignment);
    }
    public Assignment update(Long id, Assignment form, Long courseId) {
        Assignment a = findById(id);
        a.setTitle(form.getTitle()); a.setDescription(form.getDescription()); a.setDueDate(form.getDueDate()); a.setStatus(form.getStatus());
        a.setPriority(form.getPriority());
        a.setCourse(courseId == null ? null : courses.findById(courseId).orElseThrow(() -> new ResourceNotFoundException("Курс не найден: " + courseId)));
        return assignments.save(a);
    }
    public void toggleCompleted(Long id) {
        Assignment assignment = findById(id);
        assignment.setCompleted(!assignment.isCompleted());
        assignments.save(assignment);
    }
    public void delete(Long id) { assignments.delete(findById(id)); }
}
