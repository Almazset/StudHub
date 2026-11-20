package kz.studhub.web;

import java.time.LocalDate;
import java.util.UUID;
import kz.studhub.model.Assignment;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.AssignmentRepository;
import kz.studhub.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountDeletionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired AssignmentRepository assignments;
    @Autowired JdbcTemplate jdbc;
    UserAccount admin;
    UserAccount account;

    @BeforeEach
    void createAccounts() {
        admin = account("admin");
        account = account("owner");
    }

    private UserAccount account(String name) {
        UserAccount user = new UserAccount();
        user.setFullName(name);
        user.setEmail(UUID.randomUUID() + "@delete.invalid");
        user.setPassword("password-for-testing");
        return users.saveAndFlush(user);
    }

    @Test
    void selfDeletionRejectedAndEmptyUserCanBeDeleted() throws Exception {
        mvc.perform(delete("/api/users/" + admin.getId()).with(user(admin.getEmail()).roles("ADMIN")).with(csrf()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("SELF"));
        mvc.perform(delete("/api/users/" + account.getId()).with(user(admin.getEmail()).roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());
        assertThat(users.existsById(account.getId())).isFalse();
    }

    @Test
    void personalAssignmentPreventsDeletionAndRemainsPrivate() throws Exception {
        Assignment assignment = new Assignment();
        assignment.setTitle("Private work");
        assignment.setDueDate(LocalDate.now().plusDays(1));
        assignment.setStudent(account);
        assignments.saveAndFlush(assignment);
        rejected();
        assertThat(assignments.findById(assignment.getId()).orElseThrow().getStudent().getId())
            .isEqualTo(account.getId());
    }

    @Test
    void projectWithNumericOwnerPreventsDeletion() throws Exception {
        jdbc.update("insert into study_projects (owner_id, title, description, closed) values (?, ?, ?, ?)",
            account.getId(), "Team project", "", false);
        rejected();
        assertThat(jdbc.queryForObject("select count(*) from study_projects where owner_id = ?", Long.class, account.getId()))
            .isEqualTo(1);
    }

    @Test
    void cascadeOwnedStudyPlanPreventsDeletion() throws Exception {
        jdbc.update("insert into study_plan_items (owner_id, title, planned_date, minutes, description, completed) values (?, ?, ?, ?, ?, ?)",
            account.getId(), "Study", LocalDate.now(), 30, "", false);
        rejected();
        assertThat(jdbc.queryForObject("select count(*) from study_plan_items where owner_id = ?", Long.class, account.getId()))
            .isEqualTo(1);
    }

    private void rejected() throws Exception {
        mvc.perform(delete("/api/users/" + account.getId()).with(user(admin.getEmail()).roles("ADMIN")).with(csrf()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("LINKED_DATA"));
        assertThat(users.existsById(account.getId())).isTrue();
    }
}
