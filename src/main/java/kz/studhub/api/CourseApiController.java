package kz.studhub.api;

import kz.studhub.model.Course;
import kz.studhub.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseApiController {
    private final CourseService service;
    public CourseApiController(CourseService service) { this.service = service; }
    @GetMapping public List<Course> all() { return service.findAll(); }
    @GetMapping("/{id}") public Course one(@PathVariable Long id) { return service.findById(id); }
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PostMapping public Course create(@Valid @RequestBody Course course) { course.setId(null); return service.save(course); }
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}") public Course update(@PathVariable Long id, @Valid @RequestBody Course course) { return service.update(id, course); }
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}") public void delete(@PathVariable Long id) { service.delete(id); }
}
