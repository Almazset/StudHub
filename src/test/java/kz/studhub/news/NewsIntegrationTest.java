package kz.studhub.news;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import kz.studhub.model.Role;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NewsIntegrationTest {
    private static final String SOURCE_URL = "https://library.example/series";
    private static final String SOURCE_NAME = "Библиотека учебных материалов";
    private static final String SOURCE_DATE = "2025-09-10";

    @Autowired MockMvc mvc;
    @Autowired NewsRepository news;
    @Autowired UserAccountRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test void managementRequiresAdministratorAndCsrf() throws Exception {
        var existing = createPost("Доступ к новостям", "ANNOUNCEMENT");

        mvc.perform(get("/news")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/news/manage").with(user("reader"))).andExpect(status().isForbidden());
        mvc.perform(get("/news/new").with(user("reader"))).andExpect(status().isForbidden());
        mvc.perform(get("/news/{id}/edit", existing.getId()).with(user("reader"))).andExpect(status().isForbidden());
        mvc.perform(get("/news/new").with(editor())).andExpect(status().isOk());

        long before = news.count();
        mvc.perform(form("/news", "Новая публикация", "ANNOUNCEMENT").with(user("reader")).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(form("/news", "Новая публикация", "ANNOUNCEMENT").with(editor()))
            .andExpect(status().isForbidden());
        mvc.perform(form("/news/{id}", "Чужое изменение", "ANNOUNCEMENT", existing.getId()).with(user("reader")).with(csrf()))
            .andExpect(status().isForbidden());
        for (String action : new String[]{"publish", "unpublish", "delete"}) {
            String route = "/news/{id}/" + action;
            mvc.perform(post(route, existing.getId()).with(user("reader")).with(csrf()))
                .andExpect(status().isForbidden());
            mvc.perform(post(route, existing.getId()).with(editor()))
                .andExpect(status().isForbidden());
        }
        assertThat(news.count()).isEqualTo(before);
        assertThat(findByTitle("Доступ к новостям").isPublished()).isTrue();
    }

    @Test void readersCanFilterFourCategoriesAndSeeStatusOnUpdates() throws Exception {
        var titles = Map.of("UPDATE", "Переработка панели", "COURSE", "Курс Java",
            "ANNOUNCEMENT", "Приглашение на семинар", "USEFUL", "Список тренажёров");
        for (var entry : titles.entrySet()) {
            createPost(entry.getValue(), entry.getKey());
        }
        var all = mvc.perform(get("/news").with(user("reader"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("Обновления StudHub")));
        for (String title : titles.values()) {
            all.andExpect(content().string(containsString(title)));
        }
        for (var entry : titles.entrySet()) {
            var filtered = mvc.perform(get("/news").param("category", entry.getKey()).with(user("reader")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(entry.getValue())));
            for (var other : titles.entrySet()) {
                if (!other.getKey().equals(entry.getKey())) {
                    filtered.andExpect(content().string(not(containsString(other.getValue()))));
                }
            }
            if (!entry.getKey().equals("UPDATE")) {
                filtered.andExpect(content().string(not(containsString("data-news-status"))));
            }
        }

        var statuses = Map.of("PLANNED", "Планируется", "IN_PROGRESS", "В разработке", "RELEASED", "Выпущено");
        for (var entry : statuses.entrySet()) {
            String title = "Статус обновления " + entry.getKey();
            submit(form("/news", title, "UPDATE").param("status", entry.getKey()))
                .andExpect(status().is3xxRedirection());
            var saved = findByTitle(title);
            assertThat(saved.getStatus().name()).isEqualTo(entry.getKey());
            mvc.perform(get("/news/{id}", saved.getId()).with(user("reader")))
                .andExpect(status().isOk()).andExpect(content().string(containsString(entry.getValue())));
        }
        submit(form("/news", "Обновление без статуса", "UPDATE"))
            .andExpect(status().isOk()).andExpect(model().attributeHasErrors("newsForm"));

        var updated = findByTitle("Статус обновления IN_PROGRESS");
        Long recategorizedId = updated.getId();
        LocalDateTime firstPublishedAt = updated.getPublishedAt();
        submit(form("/news/{id}", "Перенесено в анонсы", "ANNOUNCEMENT", recategorizedId)
                .param("status", "IN_PROGRESS"))
            .andExpect(status().is3xxRedirection());
        var recategorized = news.findById(recategorizedId).orElseThrow();
        assertThat(recategorized.getStatus()).isNull();
        assertThat(recategorized.getPublishedAt()).isEqualTo(firstPublishedAt);
    }

    @Test void showsExternalSourceAndKeepsSourceDateSeparateFromEvent() throws Exception {
        submit(form("/news", "Внешний учебный материал", "USEFUL")
            .param("link", SOURCE_URL).param("sourceName", SOURCE_NAME).param("sourceDate", SOURCE_DATE)
            .param("eventDate", "2025-10-01"))
            .andExpect(status().is3xxRedirection());
        var saved = findByTitle("Внешний учебный материал");
        assertThat(saved.getSourceName()).isEqualTo(SOURCE_NAME);
        assertThat(saved.getSourceDate()).isEqualTo(LocalDate.parse(SOURCE_DATE));
        assertThat(saved.getEventDate()).isEqualTo(LocalDate.parse("2025-10-01"));

        for (String url : new String[]{"/news?category=USEFUL", "/news/" + saved.getId()}) {
            mvc.perform(get(url).with(user("reader"))).andExpect(status().isOk())
                .andExpect(content().string(containsString(SOURCE_NAME)))
                .andExpect(content().string(containsString("datetime=\"" + SOURCE_DATE + "\"")))
                .andExpect(content().string(containsString("href=\"" + SOURCE_URL + "\"")))
                .andExpect(content().string(containsString("target=\"_blank\"")))
                .andExpect(content().string(containsString("rel=\"noopener noreferrer\"")));
        }

        submit(form("/news", "Внутренний курс", "COURSE").param("link", "/courses/123"))
            .andExpect(status().is3xxRedirection());
        var internal = findByTitle("Внутренний курс");
        assertThat(internal.isExternal()).isFalse();
        mvc.perform(get("/news/{id}", internal.getId()).with(user("reader")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("href=\"/courses/123\"")));
    }

    @Test void externalSourceRequiresBothFieldsAndRejectsUnsafeAddresses() throws Exception {
        long before = news.count();
        for (String missing : new String[]{"sourceName", "sourceDate"}) {
            var incomplete = form("/news", "Незаполненный источник", "USEFUL").param("link", SOURCE_URL);
            if (!missing.equals("sourceName")) incomplete.param("sourceName", SOURCE_NAME);
            if (!missing.equals("sourceDate")) incomplete.param("sourceDate", SOURCE_DATE);
            submit(incomplete).andExpect(status().isOk()).andExpect(model().attributeHasErrors("newsForm"));
        }
        for (String unsafe : new String[]{"javascript:alert(1)", "//evil.example",
            "/courses/1/../../logout", "https://trusted.example@evil.example/article"}) {
            submit(form("/news", "Небезопасная ссылка", "USEFUL")
                .param("link", unsafe).param("sourceName", SOURCE_NAME).param("sourceDate", SOURCE_DATE))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("newsForm"));
        }
        submit(form("/news", "Некорректная дата", "USEFUL").param("link", SOURCE_URL)
            .param("sourceName", SOURCE_NAME).param("sourceDate", "не дата"))
            .andExpect(status().isOk()).andExpect(model().attributeHasErrors("newsForm"));
        assertThat(news.count()).isEqualTo(before);
    }

    @Test void publicationTransitionsControlReaderDetailFeedAndHome() throws Exception {
        String reader = account("dashboard-reader@news.example");
        String title = "Закрытый анонс семинара";
        submit(form("/news", title, "ANNOUNCEMENT").param("intent", "draft"))
            .andExpect(status().is3xxRedirection());
        var draft = findByTitle(title);
        Long id = draft.getId();
        assertThat(draft.isPublished()).isFalse();
        assertThat(draft.getPublishedAt()).isNull();

        assertHiddenFromReaders(id, title, reader);
        mvc.perform(get("/news/{id}", id).with(editor())).andExpect(status().isOk())
            .andExpect(content().string(containsString(title)));

        submit(post("/news/{id}/publish", id))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/news/manage"));
        var published = news.findById(id).orElseThrow();
        assertThat(published.isPublished()).isTrue();
        assertThat(published.getPublishedAt()).isNotNull();
        var firstPublication = published.getPublishedAt();
        for (String url : new String[]{"/news", "/news/" + id, "/"}) {
            mvc.perform(get(url).with(user(reader))).andExpect(status().isOk())
                .andExpect(content().string(containsString(title)));
        }

        submit(post("/news/{id}/unpublish", id))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/news/manage"));
        assertHiddenFromReaders(id, title, reader);
        submit(post("/news/{id}/publish", id))
            .andExpect(status().is3xxRedirection());
        assertThat(news.findById(id).orElseThrow().getPublishedAt()).isEqualTo(firstPublication);
    }

    @Test void editorCanFilterEditAndDeleteWithoutPublishingDraftsAccidentally() throws Exception {
        createPost("Готовый Java курс", "COURSE");
        createPost("Готовый Kotlin курс", "COURSE");
        createPost("Готовый Java анонс", "ANNOUNCEMENT");
        submit(form("/news", "Скрытый Java курс", "COURSE").param("intent", "draft"))
            .andExpect(status().is3xxRedirection());
        Long id = findByTitle("Скрытый Java курс").getId();

        mvc.perform(get("/news/manage").with(editor())
                .param("category", "COURSE").param("state", "published").param("q", "jAvA"))
            .andExpect(status().isOk()).andExpect(view().name("news-manage"))
            .andExpect(content().string(containsString("Готовый Java курс")))
            .andExpect(content().string(not(containsString("Готовый Kotlin курс"))))
            .andExpect(content().string(not(containsString("Готовый Java анонс"))))
            .andExpect(content().string(not(containsString("Скрытый Java курс"))));
        mvc.perform(get("/news/{id}/edit", id).with(editor())).andExpect(status().isOk());
        submit(form("/news/{id}", "Изменённый черновик курса", "COURSE", id))
            .andExpect(status().is3xxRedirection());
        assertThat(news.findById(id).orElseThrow().isPublished()).isFalse();
        mvc.perform(get("/news/manage").with(editor()).param("state", "draft"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Изменённый черновик курса")));

        submit(post("/news/{id}/delete", id).param("from", "manage"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/news/manage"));
        assertThat(news.existsById(id)).isFalse();
    }

    @Test void legacyExternalPostStaysReadableWithoutInventingSourceDateOrStatus() throws Exception {
        String title = "Ранее опубликованная внешняя новость";
        LocalDateTime publishedAt = LocalDateTime.of(2025, 5, 12, 10, 30);
        jdbc.update("""
            insert into news_posts (title, body, category, link, published_at)
            values (?, ?, ?, ?, ?)
            """, title, "Текст из прежней версии", "UPDATE", SOURCE_URL, Timestamp.valueOf(publishedAt));
        var legacy = findByTitle(title);
        assertThat(legacy.isPublished()).isTrue();
        assertThat(legacy.getSourceDate()).isNull();
        assertThat(legacy.getStatus()).isNull();
        assertThat(legacy.getPublishedAt()).isEqualTo(publishedAt);
        mvc.perform(get("/news/{id}", legacy.getId()).with(user("reader")))
            .andExpect(status().isOk()).andExpect(content().string(containsString(title)))
            .andExpect(content().string(containsString("library.example")))
            .andExpect(content().string(containsString("Дата источника не указана")))
            .andExpect(content().string(not(containsString("data-news-status"))));
        mvc.perform(get("/news").param("category", "UPDATE").with(user("reader")))
            .andExpect(status().isOk()).andExpect(content().string(containsString(title)));
        mvc.perform(get("/news/{id}/edit", legacy.getId()).with(editor())).andExpect(status().isOk());
    }

    private void assertHiddenFromReaders(Long id, String title, String reader) throws Exception {
        mvc.perform(get("/news/{id}", id).with(user(reader))).andExpect(status().isNotFound());
        for (String url : new String[]{"/news", "/"}) {
            mvc.perform(get(url).with(user(reader))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString(title))));
        }
    }

    private NewsPost createPost(String title, String category) throws Exception {
        var request = form("/news", title, category);
        if (category.equals("UPDATE")) request.param("status", "RELEASED");
        submit(request).andExpect(status().is3xxRedirection());
        return findByTitle(title);
    }

    private NewsPost findByTitle(String title) {
        return news.findAll().stream().filter(post -> post.getTitle().equals(title)).findFirst().orElseThrow();
    }

    private String account(String email) {
        UserAccount account = new UserAccount();
        account.setFullName("Читатель StudHub");
        account.setEmail(email);
        account.setPassword("not-a-real-encoded-password");
        account.setRole(Role.USER);
        return users.saveAndFlush(account).getEmail();
    }

    private MockHttpServletRequestBuilder form(String route, String title, String category, Object... pathVariables) {
        return post(route, pathVariables).param("title", title).param("body", "Текст публикации").param("category", category);
    }

    private ResultActions submit(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(editor()).with(csrf()));
    }

    private RequestPostProcessor editor() {
        return user("editor").roles("ADMIN");
    }
}
