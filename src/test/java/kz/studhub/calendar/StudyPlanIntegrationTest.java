package kz.studhub.calendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
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
    "spring.datasource.url=jdbc:h2:mem:study-plan;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "spring.jackson.time-zone=America/Los_Angeles",
    "studhub.ai.gemini-api-key=",
    "studhub.security.key-file=target/study-plan-test-security.key"
})
@AutoConfigureMockMvc
class StudyPlanIntegrationTest {
    private static final String PATH = "/api/calendar/plan";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserAccountRepository users;
    @Autowired StudyPlanRepository plans;
    @Autowired StudyPlanService service;
    UserAccount alice;
    UserAccount bob;

    @BeforeEach
    void accounts() {
        alice = account("Alice");
        bob = account("Bob");
    }

    @Test
    void privatePlanLifecycleRequiresAuthenticationAndCsrf() throws Exception {
        var input = Map.of("title", " Review vectors ", "date", "2026-11-08", "minutes", 45,
            "description", "Check notation", "completed", false, "ownerId", bob.getId());
        mvc.perform(range("2026-11-08", "2026-11-08")).andExpect(status().is3xxRedirection());
        mvc.perform(body(post(PATH), input).with(user(alice.getEmail())))
            .andExpect(status().isForbidden());

        var created = mvc.perform(body(post(PATH), input).with(user(alice.getEmail())).with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.title").value("Review vectors"))
            .andExpect(jsonPath("$.date").value("2026-11-08"))
            .andExpect(jsonPath("$.ownerId").doesNotExist())
            .andReturn();
        long id = json.readTree(created.getResponse().getContentAsByteArray()).get("id").asLong();

        mvc.perform(range("2026-11-08", "2026-11-08").with(user(alice.getEmail())))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(range("2026-11-08", "2026-11-08").with(user(bob.getEmail()).roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        String item = PATH + "/" + id;
        var updated = Map.of("title", "Vector practice", "date", "2026-11-09", "minutes", 60, "completed", true);
        mvc.perform(body(put(item), updated).with(user(bob.getEmail()).roles("ADMIN")).with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("PLAN_NOT_FOUND"));
        mvc.perform(delete(item).with(user(bob.getEmail())).with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(body(put(item), updated).with(user(alice.getEmail())))
            .andExpect(status().isForbidden());
        mvc.perform(body(put(item), updated).with(user(alice.getEmail())).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.minutes").value(60))
            .andExpect(jsonPath("$.description").value(""))
            .andExpect(jsonPath("$.completed").value(true));
        mvc.perform(range("2026-11-08", "2026-11-08").with(user(alice.getEmail())))
            .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(range("2026-11-09", "2026-11-09").with(user(alice.getEmail())))
            .andExpect(jsonPath("$[0].date").value("2026-11-09"))
            .andExpect(jsonPath("$[0].completed").value(true));
        mvc.perform(delete(item).with(user(alice.getEmail())))
            .andExpect(status().isForbidden());
        mvc.perform(delete(item).with(user(alice.getEmail())).with(csrf()))
            .andExpect(status().isNoContent())
            .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(range("2026-11-08", "2026-11-09").with(user(alice.getEmail())))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void validatesIsoDatesAndInclusiveRangesAcrossLeapDay() throws Exception {
        for (String date : List.of("2028-03-01", "2028-02-28", "2028-02-29")) {
            mvc.perform(body(post(PATH), Map.of("title", "Calculus", "date", date, "minutes", 25))
                .with(user(alice.getEmail())).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").value(date))
                .andExpect(jsonPath("$.completed").value(false));
        }

        mvc.perform(range("2028-02-28", "2028-04-29").with(user(alice.getEmail())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].date").value("2028-02-28"))
            .andExpect(jsonPath("$[1].date").value("2028-02-29"))
            .andExpect(jsonPath("$[2].date").value("2028-03-01"));
        mvc.perform(range("2028-02-28", "2028-04-30").with(user(alice.getEmail())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_RANGE"));
        mvc.perform(range("2028-03-01", "2028-02-28").with(user(alice.getEmail())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_RANGE"));
        mvc.perform(range("2027-02-29", "2027-03-01").with(user(alice.getEmail())))
            .andExpect(status().isBadRequest());
        mvc.perform(get(PATH).with(user(alice.getEmail())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_RANGE"));

        var invalidInputs = List.of(
            Map.of("title", "Plan", "date", "2027-02-29", "minutes", 25),
            Map.of("title", "Plan", "date", "2028-02-29T00:00:00Z", "minutes", 25),
            Map.of("title", "Plan", "date", "2028-02-29", "minutes", 4),
            Map.of("title", "Plan", "date", "2028-02-29", "minutes", 721),
            Map.of("title", " ", "date", "2028-02-29", "minutes", 25),
            Map.of("title", "Plan", "date", "2028-02-29", "minutes", 25, "description", "a".repeat(1001))
        );
        for (var input : invalidInputs) {
            mvc.perform(body(post(PATH), input).with(user(alice.getEmail())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION"));
        }
        assertThat(plans.countByOwnerId(alice.getId())).isEqualTo(3);
    }

    @Test
    void concurrentCreatesCannotExceedPerAccountLimit() throws Exception {
        plans.saveAll(IntStream.range(1, 500).mapToObj(number -> {
            var item = new StudyPlanItem(alice);
            item.date = LocalDate.of(2028, 2, 29);
            item.title = "Lesson " + number;
            item.minutes = 30;
            item.description = "";
            return item;
        }).toList());

        var start = new CountDownLatch(1);
        var input = new StudyPlanService.Input("Last lesson", "2028-03-01", 25, null, false);
        Callable<String> save = () -> {
            start.await();
            try {
                service.create(alice.getEmail(), input);
                return "created";
            } catch (StudyPlanService.Failure failure) {
                return failure.getMessage();
            }
        };

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(save);
            var second = pool.submit(save);
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("created", "PLAN_LIMIT");
        }
        assertThat(plans.countByOwnerId(alice.getId())).isEqualTo(500);
        assertThat(service.create(bob.getEmail(), input).date()).isEqualTo(LocalDate.of(2028, 3, 1));
    }

    private static MockHttpServletRequestBuilder range(String from, String to) {
        return get(PATH).param("from", from).param("to", to);
    }

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Map<String, ?> input)
            throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(input));
    }

    private UserAccount account(String name) {
        var account = new UserAccount();
        account.setFullName(name);
        account.setEmail(UUID.randomUUID() + "@example.com");
        account.setPassword("integration-test-only");
        return users.save(account);
    }
}
