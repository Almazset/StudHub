package kz.studhub.project;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import kz.studhub.model.Assignment;
import kz.studhub.model.Course;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.AssignmentRepository;
import kz.studhub.repository.CourseRepository;
import kz.studhub.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CalendarDeadlinesIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired AssignmentRepository assignments;
    @Autowired CourseRepository courses;
    @Autowired ProjectRepository projects;
    @Autowired ProjectTaskRepository tasks;

    @Test void calendarOnlyReturnsTheViewersDatedWorkFromOpenProjects() throws Exception {
        UserAccount alice = account();
        UserAccount bob = account();
        LocalDate day = LocalDate.of(2024, 2, 29);
        var personal = assignment(alice, "Personal", day, null, false);
        var course = new Course();
        course.setTitle("Algorithms");
        course.setDescription("Intro");
        course.setTeacherName("Teacher");
        courses.saveAndFlush(course);
        assignment(alice, "Course work", day, course, true);
        assignment(bob, "Other user's assignment", day, null, false);
        assignment(alice, "Outside the range", day.plusDays(1), null, false);

        Project active = project(bob, false, alice);
        task(active, "Assigned team task", alice, day);
        task(active, "Another member's task", bob, day);
        task(active, "Undated task", alice, null);
        task(active, "Unassigned task", null, day);
        task(project(alice, true, alice), "Closed project task", alice, day);
        task(project(bob, false, bob), "Inaccessible project task", alice, day);

        mvc.perform(get("/api/calendar").param("view", "day").param("date",day.toString()).param("zone", "UTC")
                .with(user(alice.getEmail())))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.from").value("2024-02-29"))
            .andExpect(jsonPath("$.to").value("2024-02-29"))
            .andExpect(jsonPath("$.deadlines[*].title", containsInAnyOrder("Personal", "Course work", "Assigned team task")))
            .andExpect(jsonPath("$.deadlines[?(@.kind=='personal')].url", contains("/assignments/" + personal.getId() + "/edit")))
            .andExpect(jsonPath("$.deadlines[?(@.kind=='course')].context", contains("Algorithms")))
            .andExpect(jsonPath("$.deadlines[?(@.kind=='course')].status", contains("done")))
            .andExpect(jsonPath("$.deadlines[?(@.kind=='project')].url", contains("/projects/" + active.id)))
            .andExpect(jsonPath("$.deadlines[?(@.kind=='project')].status", contains("overdue")));

        mvc.perform(get("/api/calendar").param("view", "day").param("date",day.toString()).param("zone", "UTC")
                .with(user(bob.getEmail())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deadlines[*].title", containsInAnyOrder("Other user's assignment", "Another member's task")));
    }

    @Test void focusIsIndependentOfDisplayedPeriodAndPutsTodayThenRecentOverdueFirst() throws Exception {
        var alice = account();
        var today = LocalDate.now(ZoneOffset.UTC);
        assignment(alice, "Today", today, null, false);
        assignment(alice, "Recently overdue", today.minusDays(2), null, false);
        assignment(alice, "Old deadline", today.minusWeeks(3), null, false);
        assignment(alice, "Upcoming", today.plusDays(1), null, false);
        assignment(alice, "Done", today, null, true);
        task(project(alice, false, alice), "Latest overdue", alice, today.minusDays(1));

        mvc.perform(get("/api/calendar").param("view", "month").param("date", today.plusYears(1).toString())
                .param("zone", "UTC").with(user(alice.getEmail())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deadlines", empty()))
            .andExpect(jsonPath("$.focus[*].title", contains("Today", "Latest overdue", "Recently overdue")))
            .andExpect(jsonPath("$.focus[*].status", contains("today", "overdue", "overdue")));
    }

    @Test void requiresSigninAndRejectsInvalidCalendarParameters() throws Exception {
        mvc.perform(get("/api/calendar")).andExpect(status().is3xxRedirection());
        var signedIn = user(account().getEmail());
        mvc.perform(get("/api/calendar").param("date", "2025-02-29").with(signedIn))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_CALENDAR_DATE"));
        mvc.perform(get("/api/calendar").param("view", "year").with(signedIn))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_CALENDAR_VIEW"));
        mvc.perform(get("/api/calendar").param("zone", "wrong-zone").with(signedIn))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_TIME_ZONE"));
    }

    private UserAccount account() {
        var user = new UserAccount();
        user.setEmail(UUID.randomUUID() + "@calendar.invalid");
        user.setFullName("Calendar Test");
        user.setPassword("unused-hash");
        return users.saveAndFlush(user);
    }

    private Assignment assignment(UserAccount owner, String title, LocalDate date, Course course, boolean done) {
        var assignment = new Assignment();
        assignment.setStudent(owner);
        assignment.setTitle(title);
        assignment.setDueDate(date);
        assignment.setCourse(course);
        assignment.setCompleted(done);
        return assignments.saveAndFlush(assignment);
    }

    private Project project(UserAccount owner, boolean closed, UserAccount member) {
        var project = new Project();
        project.ownerId = owner.getId();
        project.title = "Team Project";
        project.closed = closed;
        project.memberIds.add(owner.getId());
        project.memberIds.add(member.getId());
        return projects.saveAndFlush(project);
    }

    private void task(Project project, String title, UserAccount assignee, LocalDate date) {
        var task = new ProjectTask();
        task.projectId = project.id;
        task.title = title;
        task.assigneeId = assignee == null ? null : assignee.getId();
        task.dueDate = date;
        tasks.saveAndFlush(task);
    }
}
