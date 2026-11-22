package kz.studhub.web;

import kz.studhub.service.AssignmentService;
import kz.studhub.service.CourseService;
import kz.studhub.repository.UserAccountRepository;
import kz.studhub.model.Assignment;
import org.springframework.stereotype.Controller;
import org.springframework.data.domain.PageRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.IntStream;

@Controller
public class HomeController {
    private final CourseService courses;
    private final AssignmentService assignments;
    private final UserAccountRepository users;
    private final MessageSource messages;
    private final kz.studhub.project.ProjectRepository projects;
    private final kz.studhub.news.NewsRepository news;
    public HomeController(CourseService courses, AssignmentService assignments, UserAccountRepository users, MessageSource messages, kz.studhub.news.NewsRepository news, kz.studhub.project.ProjectRepository projects) {
        this.projects = projects;
        this.news = news;
        this.courses = courses; this.assignments = assignments; this.users = users; this.messages = messages;
    }
    @GetMapping("/")
    public String home(Model model, Authentication authentication) {
        model.addAttribute("latestNews", news.latestPublished(PageRequest.of(0, 3)));
        var allAssignments = assignments.findAll();
        var allCourses = courses.findAll();
        var currentAccount = users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Аккаунт не найден"));
        var visibleProjects = projects.visible(currentAccount.getId());
        model.addAttribute("projectCount", visibleProjects.size());
        model.addAttribute("visibleProjects", visibleProjects.stream().limit(6)
                .map(p -> new ProjectLink(p.getId(), p.getTitle())).toList());
        // Courses currently form a public catalog; there is no private/enrollment model.
        model.addAttribute("availableCourses", allCourses.stream().limit(6).toList());
        String greetingKey = LocalTime.now().isBefore(LocalTime.NOON) ? "dashboard.morning"
                : (LocalTime.now().isBefore(LocalTime.of(18, 0)) ? "dashboard.day" : "dashboard.evening");
        model.addAttribute("greeting", messages.getMessage(greetingKey,
                new Object[]{currentAccount.getFullName()}, LocaleContextHolder.getLocale()));
        model.addAttribute("courseCount", allCourses.size());
        model.addAttribute("assignmentCount", allAssignments.size());
        long completed = allAssignments.stream().filter(a -> a.isCompleted()).count();
        long pending = allAssignments.size() - completed;
        model.addAttribute("completedCount", completed);
        model.addAttribute("pendingCount", pending);
        model.addAttribute("completionPercent", allAssignments.isEmpty() ? 0 : Math.round(completed * 100.0 / allAssignments.size()));
        model.addAttribute("upcomingAssignments", allAssignments.stream()
                .filter(assignment -> !assignment.isCompleted())
                .sorted((left, right) -> left.getDueDate().compareTo(right.getDueDate()))
                .limit(5)
                .toList());
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        model.addAttribute("weekSlots", IntStream.range(0, 7)
                .mapToObj(offset -> {
                    LocalDate day = monday.plusDays(offset);
                    return new WeekSlot(day, allAssignments.stream()
                            .filter(assignment -> !assignment.isCompleted() && day.equals(assignment.getDueDate()))
                            .toList());
                }).toList());
        model.addAttribute("today", today);
        return "index";
    }
    @org.springframework.web.bind.annotation.RequestMapping("/access-denied") public String denied() { return "access-denied"; }

    public record ProjectLink(Long id, String title) {}

    public static class WeekSlot {
        private final LocalDate date;
        private final List<Assignment> assignments;

        public WeekSlot(LocalDate date, List<Assignment> assignments) {
            this.date = date;
            this.assignments = assignments;
        }
        public LocalDate getDate() { return date; }
        public List<Assignment> getAssignments() { return assignments; }
    }
}
