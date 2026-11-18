package kz.studhub.calendar;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import kz.studhub.model.Assignment;
import kz.studhub.project.ProjectTaskRepository;
import kz.studhub.project.ProjectTaskRepository.CalendarTask;
import kz.studhub.repository.AssignmentRepository;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CalendarDeadlinesService {
    private static final int FOCUS_LIMIT = 3;
    private static final int SOON_DAYS = 3;
    private static final Comparator<Deadline> BY_DATE = Comparator.comparing(Deadline::date)
        .thenComparing(Deadline::kind).thenComparing(Deadline::id);

    private final AssignmentRepository assignments;
    private final ProjectTaskRepository projectTasks;
    private final UserAccountRepository users;

    CalendarDeadlinesService(AssignmentRepository assignments, ProjectTaskRepository projectTasks,
                             UserAccountRepository users) {
        this.assignments = assignments;
        this.projectTasks = projectTasks;
        this.users = users;
    }

    record Deadline(Long id, String title, LocalDate date, String kind, String context, String url, String status) {}

    record CalendarResponse(String view, LocalDate date, LocalDate today, String zone, LocalDate from, LocalDate to,
                            List<Deadline> deadlines, List<Deadline> focus) {}

    @Transactional(readOnly = true)
    public CalendarResponse calendar(String email, String view, String date, String zone) {
        var window = CalendarWindow.resolve(view, date, zone, Clock.systemUTC());
        Long userId = users.findByEmailIgnoreCase(email).orElseThrow(() -> new AccessDeniedException("Account not found")).getId();
        LocalDate today = window.today();
        List<Deadline> deadlines = merge(
            assignments.findByStudentIdAndDueDateBetween(userId, window.from(), window.to()),
            projectTasks.calendarTasks(userId, window.from(), window.to()), today);
        deadlines.sort(BY_DATE);

        // Each data source contributes at most three rows. SQL and the merge below use the same urgency order.
        var limit = PageRequest.of(0, FOCUS_LIMIT);
        LocalDate through = today.plusDays(SOON_DAYS);
        List<Deadline> focus = merge(assignments.calendarFocus(userId, today, through, limit),
            projectTasks.calendarFocus(userId, today, through, limit), today);
        focus.sort(Comparator.comparingInt(CalendarDeadlinesService::urgency)
            .thenComparing(item -> item.status().equals("overdue") ? -item.date().toEpochDay() : item.date().toEpochDay())
            .thenComparing(Deadline::kind).thenComparing(Deadline::id));
        return new CalendarResponse(window.view(), window.date(), today, window.zone().getId(), window.from(),
            window.to(), List.copyOf(deadlines), focus.stream().limit(FOCUS_LIMIT).toList());
    }

    private static List<Deadline> merge(List<Assignment> assignments, List<CalendarTask> tasks, LocalDate today) {
        List<Deadline> result = new ArrayList<>(assignments.size() + tasks.size());
        for (Assignment assignment : assignments) {
            var course = assignment.getCourse();
            result.add(new Deadline(assignment.getId(), assignment.getTitle(), assignment.getDueDate(),
                course == null ? "personal" : "course", course == null ? null : course.getTitle(),
                "/assignments/" + assignment.getId() + "/edit",
                status(assignment.getDueDate(), assignment.isCompleted(), today)));
        }
        for (CalendarTask task : tasks) {
            result.add(new Deadline(task.getId(), task.getTitle(), task.getDueDate(), "project", task.getProjectTitle(),
                "/projects/" + task.getProjectId(), status(task.getDueDate(), "DONE".equals(task.getStatus()), today)));
        }
        return result;
    }

    static String status(LocalDate date, boolean completed, LocalDate today) {
        if (completed) return "done";
        if (date.equals(today)) return "today";
        if (date.isBefore(today)) return "overdue";
        if (!date.isAfter(today.plusDays(SOON_DAYS))) return "soon";
        return "upcoming";
    }

    private static int urgency(Deadline item) {
        return switch(item.status()) {
            case "today" -> 0;
            case "overdue" -> 1;
            default -> 2;
        };
    }
}
