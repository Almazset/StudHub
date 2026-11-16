package kz.studhub.web;

import kz.studhub.model.Course;
import kz.studhub.model.Assignment;
import kz.studhub.service.AssignmentService;
import kz.studhub.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import java.util.Comparator;

@Controller
@RequestMapping("/courses")
public class CoursePageController {
    private final CourseService service;
    private final AssignmentService assignments;
    public CoursePageController(CourseService service, AssignmentService assignments) {
        this.service = service;
        this.assignments = assignments;
    }

    @InitBinder("course")
    public void bindCourse(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAllowedFields("title", "description", "teacherName", "externalUrl", "startInstructions", "language", "accessInfo");
    }
    @GetMapping public String list(Model model) { model.addAttribute("courses", service.findAll()); return "courses"; }
    @GetMapping("/{id}") public String detail(@PathVariable Long id, Model model) {
        var course = service.findById(id);
        var courseAssignments = assignments.findAll().stream()
                .filter(assignment -> assignment.getCourse() != null && assignment.getCourse().getId().equals(id))
                .sorted(Comparator.comparing(Assignment::getDueDate))
                .toList();
        model.addAttribute("course", course);
        model.addAttribute("courseAssignments", courseAssignments);
        long completed = courseAssignments.stream().filter(Assignment::isCompleted).count();
        model.addAttribute("completedCount", completed);
        model.addAttribute("completionPercent", courseAssignments.isEmpty() ? 0
                : Math.round(completed * 100.0 / courseAssignments.size()));
        return "course-detail";
    }
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/new") public String createForm(Model model) { model.addAttribute("course", new Course()); return "course-form"; }
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PostMapping public String create(@Valid @ModelAttribute Course course, BindingResult result) {
        if (result.hasErrors()) return "course-form";
        service.save(course); return "redirect:/courses?saved";
    }
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/edit") public String editForm(@PathVariable Long id, Model model) { model.addAttribute("course", service.findById(id)); return "course-form"; }
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}") public String update(@PathVariable Long id, @Valid @ModelAttribute Course course, BindingResult result) {
        course.setId(id);
        if (result.hasErrors()) return "course-form";
        service.update(id, course); return "redirect:/courses?saved";
    }
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/delete") public String delete(@PathVariable Long id) { service.delete(id); return "redirect:/courses?deleted"; }
}
