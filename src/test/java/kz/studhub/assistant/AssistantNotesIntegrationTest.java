package kz.studhub.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import kz.studhub.assistant.AssistantNotesService.NoteInput;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:assistant-notes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "studhub.ai.gemini-api-key=",
    "studhub.security.key-file=target/assistant-notes-test-security.key"
})
@AutoConfigureMockMvc
class AssistantNotesIntegrationTest {
    private static final String PATH = "/api/assistant/notes";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserAccountRepository users;
    @Autowired AssistantNoteRepository notes;
    @Autowired AssistantNotesService service;
    UserAccount alice;
    UserAccount bob;

    @BeforeEach
    void accounts() {
        alice = account("Alice");
        bob = account("Bob");
    }

    @Test
    void onlyOwnerCanListReadOrDeleteSavedNote() throws Exception {
        var example = Map.of(
            "title", " Building reliable systems ",
            "body", "First read the requirements.\nThen make a plan.",
            "terms", List.of(
                Map.of("term", "Reliability", "definition", "Consistent operation"),
                Map.of("term", "Availability", "definition", "The proportion of time a service works")
            ),
            "ownerId", bob.getId()
        );

        var response = mvc.perform(create(example).with(user(alice.getEmail())).with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.title").value("Building reliable systems"))
            .andExpect(jsonPath("$.terms.length()").value(2))
            .andExpect(jsonPath("$.createdAt").exists())
            .andReturn();
        long id = json.readTree(response.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(get(PATH).with(user(alice.getEmail())))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$[0].id").value(id))
            .andExpect(jsonPath("$[0].body").doesNotExist());
        mvc.perform(get(PATH).with(user(bob.getEmail())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(PATH + "/" + id).with(user(alice.getEmail())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.body").value(example.get("body")))
            .andExpect(jsonPath("$.terms[0].term").value("Reliability"))
            .andExpect(jsonPath("$.terms[1].term").value("Availability"));
        mvc.perform(get(PATH + "/" + id).with(user(bob.getEmail()).roles("ADMIN")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("NOTE_NOT_FOUND"));
        mvc.perform(delete(PATH + "/" + id).with(user(bob.getEmail())).with(csrf()))
            .andExpect(status().isNotFound());

        mvc.perform(delete(PATH + "/" + id).with(user(alice.getEmail())))
            .andExpect(status().isForbidden());
        mvc.perform(delete(PATH + "/" + id).with(user(alice.getEmail())).with(csrf()))
            .andExpect(status().isNoContent());
        mvc.perform(get(PATH + "/" + id).with(user(alice.getEmail())))
            .andExpect(status().isNotFound());
    }

    @Test
    void authenticationAndValidationPreventInvalidWrites() throws Exception {
        var valid = Map.of("title", "Lecture", "body", "Notes", "terms", List.of());
        mvc.perform(get(PATH)).andExpect(status().is3xxRedirection());
        mvc.perform(create(valid).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(create(valid).with(user(alice.getEmail()))).andExpect(status().isForbidden());

        var oversized = Map.of("title", "Lecture", "body", "a".repeat(18001), "terms", List.of());
        var invalidTerm = Map.of("title", "Lecture", "body", "Notes",
            "terms", List.of(Map.of("term", "Reliability", "definition", " ")));
        var invalidTitle = Map.of("title", "A" + Character.toString(0), "body", "Notes", "terms", List.of());
        for (var invalid : List.of(oversized, invalidTerm, invalidTitle)) {
            mvc.perform(create(invalid).with(user(alice.getEmail())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION"));
        }
        assertThat(notes.countByOwnerId(alice.getId())).isZero();
    }

    @Test
    void concurrentSavesRespectEachAccountsNoteLimit() throws Exception {
        var now = Instant.now();
        for (int i = 1; i < 100; i++) {
            notes.save(new AssistantNote(alice, "Lecture " + i, "Notes", List.of(), now));
        }
        var start = new CountDownLatch(1);
        var input = new NoteInput("Last lecture", "Notes", List.of());
        Callable<String> save = () -> {
            start.await();
            try {
                service.create(alice.getEmail(), input);
                return "created";
            } catch (AssistantException ex) {
                return ex.code();
            }
        };
        try (var concurrent = Executors.newFixedThreadPool(2)) {
            var first = concurrent.submit(save);
            var second = concurrent.submit(save);
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder("created", "NOTE_LIMIT");
        }
        assertThat(notes.countByOwnerId(alice.getId())).isEqualTo(100);
        assertThat(service.create(bob.getEmail(), input).title()).isEqualTo("Last lecture");
    }

    private MockHttpServletRequestBuilder create(Map<String, ?> input) throws Exception {
        return post(PATH).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(input));
    }

    private UserAccount account(String name) {
        var account = new UserAccount();
        account.setFullName(name);
        account.setEmail(UUID.randomUUID() + "@example.com");
        account.setPassword("integration-test-only");
        return users.save(account);
    }
}
