package kz.studhub.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import kz.studhub.model.Assignment;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.AssignmentRepository;
import kz.studhub.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static kz.studhub.notification.NotificationType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:notification-inbox;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "studhub.ai.gemini-api-key=",
    "studhub.security.key-file=target/notification-test-security.key"
})
@AutoConfigureMockMvc
class NotificationInboxIntegrationTest {
    private static final String PATH = "/api/notifications";

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired AssignmentRepository assignments;
    @Autowired NotificationService service;
    @Autowired InboxRepository inbox;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactionManager;

    private UserAccount alice;
    private UserAccount bob;

    @BeforeEach void accounts() {
        alice = account("Alice");
        bob = account("Bob");
    }

    @Test void feedAndReadActionsArePrivateAndRequireCsrf() throws Exception {
        service.publish(alice.getId(), FRIEND_REQUEST, "friendship:11", "", "/friends");
        service.publish(alice.getId(), PROJECT_INVITATION, "invitation:4", "", "/projects");
        var item = service.feed(alice.getId()).items().getFirst();
        String markRead = PATH + "/" + item.id() + "/read";

        mvc.perform(get(PATH)).andExpect(status().is3xxRedirection());
        mvc.perform(get(PATH).with(user(alice.getEmail())))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.unread").value(2))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].recipientId").doesNotExist());
        mvc.perform(get(PATH).with(user(bob.getEmail()).roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unread").value(0))
            .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(post(markRead).with(user(bob.getEmail()).roles("ADMIN")).with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(post(markRead).with(user(alice.getEmail())))
            .andExpect(status().isForbidden());
        mvc.perform(post(markRead).with(user(alice.getEmail())).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(post(markRead).with(user(alice.getEmail())).with(csrf()))
            .andExpect(status().isNoContent());
        assertThat(service.feed(alice.getId()).unread()).isEqualTo(1);
        mvc.perform(post(PATH + "/read-all").with(user(alice.getEmail())).with(csrf()))
            .andExpect(status().isNoContent());
        assertThat(service.feed(alice.getId()).unread()).isZero();
        assertThat(service.feed(alice.getId()).items()).allMatch(NotificationService.Item::read);
    }

    @Test void settingsFilterExistingAndFutureEventsForOnlyTheOwner() throws Exception {
        service.publish(alice.getId(), CHAT_MESSAGE, "room:1", "", "/chat?room=1");
        mvc.perform(get(PATH + "/preferences").with(user(alice.getEmail())))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.CHAT_MESSAGE").value(true))
            .andExpect(jsonPath("$.DEADLINE").value(true));

        var settings = Map.of(CHAT_MESSAGE, false, PROJECT_INVITATION, true, FRIEND_REQUEST, true,
            TASK_ASSIGNED, true, DEADLINE, true);
        mvc.perform(put(PATH + "/preferences").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(settings)).with(user(alice.getEmail())))
            .andExpect(status().isForbidden());
        mvc.perform(put(PATH + "/preferences").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(settings)).with(user(alice.getEmail())).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.CHAT_MESSAGE").value(false));
        mvc.perform(put(PATH + "/preferences").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"CHAT_MESSAGE":"false","PROJECT_INVITATION":true,"FRIEND_REQUEST":true,"TASK_ASSIGNED":true,"DEADLINE":true}
                    """).with(user(alice.getEmail())).with(csrf()))
            .andExpect(status().isBadRequest());
        mvc.perform(put(PATH + "/preferences").contentType(MediaType.APPLICATION_JSON)
                .content("{\"CHAT_MESSAGE\":true}").with(user(alice.getEmail())).with(csrf()))
            .andExpect(status().isBadRequest());
        service.publish(alice.getId(), CHAT_MESSAGE, "room:2", "", "/chat?room=2");
        service.publish(bob.getId(), CHAT_MESSAGE, "room:2", "", "/chat?room=2");
        service.publish(alice.getId(), TASK_ASSIGNED, "assign:1", "", "/projects/3");
        assertThat(service.feed(alice.getId()).items()).extracting(NotificationService.Item::type)
            .containsExactly(TASK_ASSIGNED);
        assertThat(service.feed(alice.getId()).unread()).isEqualTo(1);
        assertThat(inbox.countByRecipientId(alice.getId())).isEqualTo(2);
        assertThat(service.feed(bob.getId()).unread()).isEqualTo(1);
        assertThat(service.preferences(bob.getId()).get(CHAT_MESSAGE)).isTrue();
    }

    @Test void sourceRollbackIsSilentAndMessagesReuseTheRoomEntry() {
        long recipient = alice.getId();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            service.publish(recipient, CHAT_MESSAGE, "room:1", "", "/chat?room=1");
            status.setRollbackOnly();
        });
        assertThat(service.feed(recipient).items()).isEmpty();

        tx.executeWithoutResult(status ->
            service.publish(recipient, CHAT_MESSAGE, "room:1", "", "/chat?room=1"));
        var original = service.feed(recipient).items().getFirst();
        service.markRead(recipient, original.id());
        assertThat(service.feed(recipient).unread()).isZero();

        service.publish(recipient, CHAT_MESSAGE, "room:1", "", "/chat?room=1");
        var updated = service.feed(recipient);
        assertThat(updated.items()).hasSize(1);
        assertThat(updated.items().getFirst().id()).isEqualTo(original.id());
        assertThat(updated.items().getFirst().createdAt()).isAfter(original.createdAt());
        assertThat(updated.unread()).isEqualTo(1);
        service.publish(recipient, FRIEND_REQUEST, "friendship:12", "", "/friends");
        service.publish(recipient, FRIEND_REQUEST, "friendship:12", "", "/friends");
        assertThat(service.feed(recipient).unread()).isEqualTo(2);
    }

    @Test void remindersRetainReadStateUntilADueDateChangesAndDropStaleRows() {
        long recipient = alice.getId();
        var reminder = new NotificationService.Reminder("assignment:5:day1", "Vectors · today", "/assignments/5/edit");
        service.remind(recipient, List.of(reminder));
        var original = service.feed(recipient).items().getFirst();
        service.markRead(recipient, original.id());
        service.remind(recipient, List.of(reminder, reminder));
        var unchanged = service.feed(recipient);
        assertThat(unchanged.unread()).isZero();
        assertThat(unchanged.items()).containsExactly(new NotificationService.Item(
            original.id(), original.type(), original.detail(), original.href(), original.createdAt(), true));

        service.remind(recipient, List.of(new NotificationService.Reminder(
            "assignment:5:day2", "Vectors · tomorrow", "/assignments/5/edit")));
        var changed = service.feed(recipient);
        assertThat(changed.unread()).isEqualTo(1);
        assertThat(changed.items()).hasSize(1);
        assertThat(changed.items().getFirst().id()).isNotEqualTo(original.id());
        service.remind(recipient, List.of());
        assertThat(service.feed(recipient).items()).isEmpty();

        var muted = new java.util.EnumMap<>(service.preferences(recipient));
        muted.put(DEADLINE, false);
        service.savePreferences(recipient, muted);
        service.remind(recipient, List.of(reminder));
        assertThat(inbox.countByRecipientId(recipient)).isZero();
    }

    @Test void storesOnlyTheNewestHistoryButPreservesActiveDeadlineAcknowledgement() {
        long recipient = alice.getId();
        Instant timestamp = Instant.now().minusSeconds(3600);
        inbox.saveAll(IntStream.range(0, NotificationWriter.MAX_HISTORY).mapToObj(index ->
            new InboxEntry(alice, TASK_ASSIGNED, "task:" + index, "", "/projects/1", timestamp.plusSeconds(index)))
            .toList());
        var reminder = new NotificationService.Reminder("assignment:5:day1", "Vectors", "/assignments/5/edit");
        service.remind(recipient, List.of(reminder));
        var saved = service.feed(recipient).items().getFirst();
        assertThat(saved.type()).isEqualTo(DEADLINE);
        service.markRead(recipient, saved.id());

        service.publish(recipient, PROJECT_INVITATION, "project:1", "", "/projects");
        assertThat(inbox.countByRecipientId(recipient)).isEqualTo(NotificationWriter.MAX_HISTORY);
        assertThat(inbox.findByRecipientIdAndTypeAndEventKey(recipient, TASK_ASSIGNED, "task:0")).isEmpty();
        assertThat(service.feed(recipient).items()).hasSize(50);
        assertThat(service.feed(recipient).unread()).isEqualTo(NotificationWriter.MAX_HISTORY - 1);
        assertThat(inbox.findById(saved.id()).orElseThrow().readAt).isNotNull();
    }

    @Test void feedGeneratesDeadlineRemindersForTheRecipientAlone() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assignment(alice, "ALICE_UPCOMING", today.plusDays(1), false);
        assignment(alice, "ALICE_COMPLETED", today.plusDays(1), true);
        assignment(alice, "ALICE_OVERDUE", today.minusDays(1), false);
        assignment(bob, "BOB_OUTSIDE_WINDOW", today.plusDays(4), false);

        mvc.perform(get(PATH).param("zone","UTC").with(user(alice.getEmail())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].type").value("DEADLINE"))
            .andExpect(jsonPath("$.items[0].detail").value("ALICE_UPCOMING · " + today.plusDays(1)));
        mvc.perform(get(PATH).param("zone","UTC").with(user(bob.getEmail())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unread").value(0))
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    private void assignment(UserAccount owner, String title, LocalDate due, boolean completed) {
        var task = new Assignment();
        task.setStudent(owner);
        task.setTitle(title);
        task.setDueDate(due);
        task.setCompleted(completed);
        assignments.saveAndFlush(task);
    }

    private UserAccount account(String name) {
        var user = new UserAccount();
        user.setFullName(name);
        user.setEmail("notifications-" + UUID.randomUUID() + "@example.invalid");
        user.setPassword("test-hash-placeholder");
        return users.saveAndFlush(user);
    }
}
