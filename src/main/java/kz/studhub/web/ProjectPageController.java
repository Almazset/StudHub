package kz.studhub.web;
import kz.studhub.project.ProjectController;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
@Controller public class ProjectPageController {
    private final ProjectController projects;
    public ProjectPageController(ProjectController projects) { this.projects = projects; }
    @GetMapping("/projects") public String projects() { return "projects"; }
    @GetMapping("/projects/{id}") public String detail(@PathVariable Long id, Authentication auth, Model model) {
        model.addAttribute("project", projects.detail(auth,id));
        return "project-detail";
    }
}
