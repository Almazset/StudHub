package kz.studhub.notification;

import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import kz.studhub.model.Assignment;
import kz.studhub.model.AssignmentStatus;
import kz.studhub.project.ProjectTaskRepository;
import kz.studhub.project.ProjectTaskRepository.CalendarTask;
import kz.studhub.repository.AssignmentRepository;
import org.junit.jupiter.api.Test;

class NotificationRemindersTest {
    private static final long ACCOUNT = 21L;
    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final ProjectTaskRepository tasks = mock(ProjectTaskRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-18T20:30:00Z"), ZoneOffset.UTC);
    private final NotificationReminders reminders = new NotificationReminders(assignments, tasks, notifications, clock);

    @Test void calculatesTheReminderWindowForTheBrowserZone() {
        reminders.refresh(ACCOUNT, "Asia/Qyzylorda");
        verify(assignments).findByStudentIdAndDueDateBetween(ACCOUNT, date("2026-11-19"), date("2026-11-22"));
        verify(tasks).calendarTasks(ACCOUNT, date("2026-11-19"), date("2026-11-22"));

        reminders.refresh(ACCOUNT, "America/Los_Angeles");
        verify(assignments).findByStudentIdAndDueDateBetween(ACCOUNT, date("2026-11-18"), date("2026-11-21"));
        verify(tasks).calendarTasks(ACCOUNT, date("2026-11-18"), date("2026-11-21"));

        reminders.refresh(ACCOUNT, "invalid-zone");
        verify(assignments, times(2)).findByStudentIdAndDueDateBetween(ACCOUNT, date("2026-11-19"), date("2026-11-22"));
    }

    @Test void suppliesOnlyIncompleteRemindersInDateOrderWithStableKeys() {
        LocalDate start = date("2026-11-19"), end = date("2026-11-22");
        Assignment open = assignment(10L, end, AssignmentStatus.TODO);
        Assignment done = assignment(11L, start, AssignmentStatus.DONE);
        CalendarTask project = task(23L, 8L, start, "IN_PROGRESS");
        CalendarTask finished = task(24L, 8L, start, "DONE");
        when(assignments.findByStudentIdAndDueDateBetween(ACCOUNT, start, end)).thenReturn(List.of(open, done));
        when(tasks.calendarTasks(ACCOUNT, start, end)).thenReturn(List.of(project, finished));

        reminders.refresh(ACCOUNT, "Asia/Qyzylorda");
        verify(notifications).remind(ACCOUNT, List.of(
            new NotificationService.Reminder("project-task:23:2026-11-19", "Project practice · 2026-11-19", "/projects/8"),
            new NotificationService.Reminder("assignment:10:2026-11-22", "Read chapter · 2026-11-22", "/assignments/10/edit")
        ));
    }

    private static LocalDate date(String value) {
        return LocalDate.parse(value);
    }

    private static Assignment assignment(long id, LocalDate due, AssignmentStatus status) {
        Assignment assignment = new Assignment();
        assignment.setId(id);
        assignment.setTitle("Read chapter");
        assignment.setDueDate(due);
        assignment.setStatus(status);
        return assignment;
    }

    private static CalendarTask task(long id, long project, LocalDate due, String status) {
        CalendarTask task = mock(CalendarTask.class);
        when(task.getId()).thenReturn(id);
        when(task.getProjectId()).thenReturn(project);
        when(task.getDueDate()).thenReturn(due);
        when(task.getTitle()).thenReturn("Project practice");
        when(task.getStatus()).thenReturn(status);
        return task;
    }
}
