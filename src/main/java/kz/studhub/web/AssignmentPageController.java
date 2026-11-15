package kz.studhub.web;

import kz.studhub.model.Assignment;
import kz.studhub.service.AssignmentService;
import kz.studhub.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@Controller
@RequestMapping("/assignments")
public class AssignmentPageController {
    private final AssignmentService service;
    private final CourseService courses;
    private final kz.studhub.project.ProjectTaskOverview projectTasks;
    public AssignmentPageController(AssignmentService service, CourseService courses, kz.studhub.project.ProjectTaskOverview projectTasks) { this.service = service; this.courses = courses; this.projectTasks = projectTasks; }

    @InitBinder("assignment")
    public void bindAssignment(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAllowedFields("title", "description", "dueDate", "completed", "priority", "status");
    }
    @GetMapping public String list(Model model, @RequestParam(required = false) Long course, java.security.Principal principal) {
        var assignments = service.findAll();
        var teamTasks = projectTasks.visible(principal.getName());
        model.addAttribute("projectTasks", teamTasks);
        model.addAttribute("taskCount", assignments.size() + teamTasks.size());
        model.addAttribute("assignedCount", teamTasks.stream().filter(kz.studhub.project.ProjectTaskOverview.Row::assignedToMe).count());
        model.addAttribute("assignments", assignments);
        model.addAttribute("courses", courses.findAll());
        model.addAttribute("selectedCourse", course);
        model.addAttribute("completedCount", assignments.stream().filter(Assignment::isCompleted).count() + teamTasks.stream().filter(kz.studhub.project.ProjectTaskOverview.Row::completed).count());
        model.addAttribute("pendingCount", assignments.stream().filter(a -> !a.isCompleted()).count() + teamTasks.stream().filter(t -> !t.completed()).count());
        model.addAttribute("overdueCount", assignments.stream().filter(a -> !a.isCompleted() && a.getDueDate().isBefore(LocalDate.now())).count() + teamTasks.stream().filter(kz.studhub.project.ProjectTaskOverview.Row::overdue).count());
        return "assignments";
    }
    @GetMapping("/new") public String createForm(Model model, @RequestParam(required = false) Long course) {
        model.addAttribute("assignment", new Assignment());
        model.addAttribute("courses", courses.findAll());
        model.addAttribute("selectedCourse", course);
        return "assignment-form";
    }
    @PostMapping public String create(@Valid @ModelAttribute Assignment assignment, BindingResult result, @RequestParam(required = false) Long courseId, Model model) {
        if (result.hasErrors()) { model.addAttribute("courses", courses.findAll()); model.addAttribute("selectedCourse", courseId); return "assignment-form"; }
        service.save(assignment, courseId); return "redirect:/assignments?saved";
    }
    @GetMapping("/{id}/edit") public String editForm(@PathVariable Long id, Model model) { model.addAttribute("assignment", service.findById(id)); model.addAttribute("courses", courses.findAll()); return "assignment-form"; }
    @PostMapping("/{id}") public String update(@PathVariable Long id, @Valid @ModelAttribute Assignment assignment, BindingResult result, @RequestParam(required = false) Long courseId, Model model) {
        assignment.setId(id);
        if (result.hasErrors()) { model.addAttribute("courses", courses.findAll()); model.addAttribute("selectedCourse", courseId); return "assignment-form"; }
        service.update(id, assignment, courseId); return "redirect:/assignments?saved";
    }
    @PostMapping("/{id}/toggle-completed") public String toggleCompleted(@PathVariable Long id) {
        service.toggleCompleted(id);
        return "redirect:/assignments?saved";
    }
    @PostMapping("/{id}/delete") public String delete(@PathVariable Long id) { service.delete(id); return "redirect:/assignments?deleted"; }
}
