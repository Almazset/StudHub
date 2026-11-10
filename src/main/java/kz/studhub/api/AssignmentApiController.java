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
    @GetMapping public List<Assignment> all() { return service.findAll(); }
    @GetMapping("/{id}") public Assignment one(@PathVariable Long id) { return service.findById(id); }
    @PostMapping public Assignment create(@Valid @RequestBody AssignmentRequest request) {
        Assignment a = request.toAssignment(); return service.save(a, request.courseId());
    }
    @PutMapping("/{id}") public Assignment update(@PathVariable Long id, @Valid @RequestBody AssignmentRequest request) {
        return service.update(id, request.toAssignment(), request.courseId());
    }
    @DeleteMapping("/{id}") public void delete(@PathVariable Long id) { service.delete(id); }

    public record AssignmentRequest(@jakarta.validation.constraints.NotBlank String title,
        String description, @jakarta.validation.constraints.NotNull java.time.LocalDate dueDate,
        @jakarta.validation.constraints.NotNull Long courseId, boolean completed, AssignmentPriority priority) {
        Assignment toAssignment() {
            Assignment a = new Assignment(); a.setTitle(title); a.setDescription(description);
            a.setDueDate(dueDate); a.setCompleted(completed); a.setPriority(priority); return a;
        }
    }
}
