package kz.studhub.notification;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import kz.studhub.calendar.StudyTimeZone;
import kz.studhub.model.AssignmentStatus;
import kz.studhub.project.ProjectTaskRepository;
import kz.studhub.repository.AssignmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NotificationReminders {
    private static final int LOOKAHEAD_DAYS = 3;
    private static final int MAX_REMINDERS = 50;
    private final AssignmentRepository assignments;
    private final ProjectTaskRepository projectTasks;
    private final NotificationService notifications;
    private final Clock clock;

    @Autowired
    public NotificationReminders(AssignmentRepository assignments, ProjectTaskRepository projectTasks,
                                 NotificationService notifications) {
        this(assignments, projectTasks, notifications, Clock.systemUTC());
    }

    NotificationReminders(AssignmentRepository assignments, ProjectTaskRepository projectTasks,
                          NotificationService notifications, Clock clock) {
        this.assignments = assignments;
        this.projectTasks = projectTasks;
        this.notifications = notifications;
        this.clock = clock;
    }

    public void refresh(long recipient, String requestedZone) {
        LocalDate today = LocalDate.now(clock.withZone(resolveZone(requestedZone)));
        LocalDate through = today.plusDays(LOOKAHEAD_DAYS);
        var due = new ArrayList<Due>();
        for (var assignment : assignments.findByStudentIdAndDueDateBetween(recipient, today, through)) {
            if (!assignment.isCompleted() && assignment.getStatus() != AssignmentStatus.DONE) {
                due.add(new Due("assignment:" + assignment.getId(), assignment.getTitle(), assignment.getDueDate(),
                    "/assignments/" + assignment.getId() + "/edit"));
            }
        }
        for (var task : projectTasks.calendarTasks(recipient, today, through)) {
            if (!"DONE".equals(task.getStatus())) {
                due.add(new Due("project-task:" + task.getId(), task.getTitle(), task.getDueDate(),
                    "/projects/" + task.getProjectId()));
            }
        }

        var reminders = due.stream().sorted(Comparator.comparing(Due::date).thenComparing(Due::key))
            .limit(MAX_REMINDERS).map(Due::reminder).toList();
        notifications.remind(recipient, reminders);
    }

    private static ZoneId resolveZone(String requestedZone) {
        try {
            return StudyTimeZone.resolve(requestedZone);
        } catch (DateTimeException ignored) {
            // Some browser time zone aliases can be newer than the server's zone database.
            return StudyTimeZone.resolve(null);
        }
    }

    private record Due(String key, String title, LocalDate date, String href) {
        NotificationService.Reminder reminder() {
            String label = title.codePointCount(0, title.length()) > 100
                ? title.substring(0, title.offsetByCodePoints(0, 99)) + "…" : title;
            return new NotificationService.Reminder(key + ":" + date, label + " · " + date, href);
        }
    }
}
