package kz.studhub.api;

import kz.studhub.model.Assignment;
import kz.studhub.model.AssignmentPriority;
import kz.studhub.service.AssignmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentApiController {
    private final AssignmentService service;
    public AssignmentApiController(AssignmentService service) { this.service = service; }
    @GetMapping public List<AssignmentResponse> all() { return service.findAll().stream().map(AssignmentResponse::from).toList(); }
    @GetMapping("/{id}") public AssignmentResponse one(@PathVariable Long id) { return AssignmentResponse.from(service.findById(id)); }
    @PostMapping public AssignmentResponse create(@Valid @RequestBody AssignmentRequest request) {
        Assignment a = request.toAssignment(); return AssignmentResponse.from(service.save(a, request.courseId()));
    }
    @PutMapping("/{id}") public AssignmentResponse update(@PathVariable Long id, @Valid @RequestBody AssignmentRequest request) {
        return AssignmentResponse.from(service.update(id, request.toAssignment(), request.courseId()));
    }
    @DeleteMapping("/{id}") public void delete(@PathVariable Long id) { service.delete(id); }

    // Explicit response fields keep persistence proxies and account data out of JSON.
    public record AssignmentResponse(Long id, String title, String description, java.time.LocalDate dueDate,
                                     boolean completed, AssignmentPriority priority, CourseResponse course) {
        static AssignmentResponse from(Assignment a) {
            var c = a.getCourse();
            return new AssignmentResponse(a.getId(), a.getTitle(), a.getDescription(), a.getDueDate(),
                a.isCompleted(), a.getPriority(), new CourseResponse(c.getId(), c.getTitle(),
                c.getDescription(), c.getTeacherName(), c.getExternalUrl()));
        }
    }
    public record CourseResponse(Long id, String title, String description, String teacherName, String externalUrl) {}

    public record AssignmentRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min=2,max=150) String title,
        @jakarta.validation.constraints.Size(max=1500) String description, @jakarta.validation.constraints.NotNull java.time.LocalDate dueDate,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long courseId, boolean completed, AssignmentPriority priority) {
        Assignment toAssignment() {
            Assignment a = new Assignment(); a.setTitle(title); a.setDescription(description);
            a.setDueDate(dueDate); a.setCompleted(completed); a.setPriority(priority); return a;
        }
    }
}
