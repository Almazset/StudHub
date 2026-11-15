package kz.studhub.project;

import kz.studhub.model.Assignment;
import kz.studhub.model.Course;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.AssignmentRepository;
import kz.studhub.repository.CourseRepository;
import kz.studhub.repository.UserAccountRepository;
import kz.studhub.web.HomeController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardPrivacyTest {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired AssignmentRepository assignments;
    @Autowired CourseRepository courses;
    @Autowired ProjectRepository projects;
    UserAccount alice, bob, newcomer;
    Course course;
    Project shared;

    @BeforeEach void setup() {
        alice = account("dashboard-alice");
        bob = account("dashboard-bob");
        newcomer = account("dashboard-new");
        course = new Course();
        course.setTitle("Public dashboard course");
        course.setTeacherName("Teacher");
        courses.saveAndFlush(course);
        task(alice, "ALICE_TODAY", LocalDate.now(), false);
        task(alice, "ALICE_OVERDUE", LocalDate.now().minusWeeks(2), false);
        task(alice, "ALICE_DONE", LocalDate.now(), true);
        task(bob, "BOB_NEXT_WEEK", LocalDate.now().plusWeeks(2), false);
        project("ALICE_PRIVATE_PROJECT", alice);
        project("BOB_PRIVATE_PROJECT", bob);
        project("BOB_SECOND_PROJECT", bob);
        shared = project("SHARED_PROJECT", alice);
        shared.memberIds.add(bob.getId());
        projects.saveAndFlush(shared);
    }

    private UserAccount account(String name) {
        var account = new UserAccount();
        account.setEmail(name + "@dashboard.invalid");
        account.setFullName(name);
        account.setPassword("unused-test-hash");
        return users.saveAndFlush(account);
    }

    private Assignment task(UserAccount owner, String title, LocalDate due, boolean completed) {
        var task = new Assignment();
        task.setStudent(owner); task.setCourse(course);
        task.setTitle(title); task.setDueDate(due); task.setCompleted(completed);
        return assignments.saveAndFlush(task);
    }

    private Project project(String title, UserAccount owner) {
        var project = new Project();
        project.title = title; project.ownerId = owner.getId();
        project.memberIds.add(owner.getId());
        return projects.saveAndFlush(project);
    }

    @Test void accountsSeeDifferentPersonalStatisticsAndOnlyAccessibleProjects() throws Exception {
        var result = mvc.perform(get("/").with(user(alice.getEmail())))
            .andExpect(status().isOk())
            .andExpect(model().attribute("assignmentCount", 3))
            .andExpect(model().attribute("completedCount", 1L))
            .andExpect(model().attribute("pendingCount", 2L))
            .andExpect(model().attribute("completionPercent", 33L))
            .andExpect(model().attribute("projectCount", 2))
            .andExpect(model().attribute("courseCount", (int) courses.count()))
            .andExpect(model().attributeDoesNotExist("userCount"))
            .andExpect(content().string(containsString("ALICE_PRIVATE_PROJECT")))
            .andExpect(content().string(containsString("SHARED_PROJECT")))
            .andExpect(content().string(not(containsString("BOB_PRIVATE_PROJECT"))))
            .andExpect(content().string(not(containsString("BOB_NEXT_WEEK"))))
            .andExpect(content().string(containsString("Public dashboard course")))
            .andReturn();
        var model = result.getModelAndView().getModel();
        @SuppressWarnings("unchecked")
        var deadlines = (List<Assignment>) model.get("upcomingAssignments");
        assertThat(deadlines).extracting(Assignment::getTitle).containsExactly("ALICE_OVERDUE", "ALICE_TODAY");
        @SuppressWarnings("unchecked")
        var week = (List<HomeController.WeekSlot>) model.get("weekSlots");
        assertThat(week).hasSize(7);
        assertThat(week.stream().flatMap(day -> day.getAssignments().stream()))
            .extracting(Assignment::getTitle).containsExactly("ALICE_TODAY");

        mvc.perform(get("/").with(user(bob.getEmail())))
            .andExpect(status().isOk())
            .andExpect(model().attribute("assignmentCount", 1))
            .andExpect(model().attribute("completedCount", 0L))
            .andExpect(model().attribute("pendingCount", 1L))
            .andExpect(model().attribute("projectCount", 3))
            .andExpect(model().attribute("deadlineAlerts", empty()))
            .andExpect(content().string(containsString("SHARED_PROJECT")))
            .andExpect(content().string(containsString("BOB_PRIVATE_PROJECT")))
            .andExpect(content().string(not(containsString("ALICE_PRIVATE_PROJECT"))))
            .andExpect(content().string(not(containsString("ALICE_TODAY"))));
    }

    @Test void membershipRevocationRemovesProjectFromDashboard() throws Exception {
        shared.memberIds.remove(bob.getId());
        projects.saveAndFlush(shared);
        mvc.perform(get("/").with(user(bob.getEmail())))
            .andExpect(status().isOk())
            .andExpect(model().attribute("projectCount", 2))
            .andExpect(content().string(not(containsString("SHARED_PROJECT"))));
    }

    @Test void newAccountHasZeroPersonalCountsAndUsefulNextSteps() throws Exception {
        mvc.perform(get("/").with(user(newcomer.getEmail())))
            .andExpect(status().isOk())
            .andExpect(model().attribute("assignmentCount", 0))
            .andExpect(model().attribute("pendingCount", 0L))
            .andExpect(model().attribute("completedCount", 0L))
            .andExpect(model().attribute("projectCount", 0))
            .andExpect(model().attribute("upcomingAssignments", empty()))
            .andExpect(model().attribute("deadlineAlerts", empty()))
            .andExpect(content().string(containsString("Ваш учебный план пока пуст")))
            .andExpect(content().string(containsString("Вы пока не участвуете в проектах")))
            .andExpect(content().string(containsString("Public dashboard course")))
            .andExpect(content().string(not(containsString("SHARED_PROJECT"))))
            .andExpect(content().string(not(containsString("ALICE_TODAY"))))
            .andExpect(content().string(not(containsString("BOB_NEXT_WEEK"))));
    }

    @Test void completedPlanIsDifferentFromNewAccount() throws Exception {
        task(newcomer, "FINISHED_ONLY", LocalDate.now(), true);
        mvc.perform(get("/").with(user(newcomer.getEmail())))
            .andExpect(status().isOk())
            .andExpect(model().attribute("completionPercent", 100L))
            .andExpect(model().attribute("pendingCount", 0L))
            .andExpect(content().string(containsString("Все задания выполнены")))
            .andExpect(content().string(not(containsString("Ваш учебный план пока пуст"))));
    }

    @Test void emptyCatalogHasActionableExplanation() throws Exception {
        assignments.deleteAll(); assignments.flush();
        courses.deleteAll(); courses.flush();
        mvc.perform(get("/").with(user(newcomer.getEmail())))
            .andExpect(status().isOk())
            .andExpect(model().attribute("courseCount", 0))
            .andExpect(content().string(containsString("В каталоге пока нет курсов")));
    }
}
