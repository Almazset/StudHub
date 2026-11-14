package kz.studhub.web;

import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import kz.studhub.service.AssignmentService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import kz.studhub.model.Assignment;

@ControllerAdvice(annotations = Controller.class)
public class CurrentUserAdvice {
    private final UserAccountRepository users;
    private final AssignmentService assignments;

    public CurrentUserAdvice(UserAccountRepository users, AssignmentService assignments) {
        this.users = users;
        this.assignments = assignments;
    }

    @ModelAttribute("currentUser")
    public UserAccount currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return users.findByEmailIgnoreCase(authentication.getName()).orElse(null);
    }

    @ModelAttribute("deadlineAlerts")
    public List<Assignment> deadlineAlerts(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return List.of();
        }
        if (currentUser(authentication) == null) return List.of();
        LocalDate lastDay = LocalDate.now().plusDays(7);
        return assignments.findAll().stream()
                .filter(assignment -> !assignment.isCompleted() && !assignment.getDueDate().isAfter(lastDay))
                .sorted(Comparator.comparing(Assignment::getDueDate))
                .limit(5)
                .toList();
    }
}
