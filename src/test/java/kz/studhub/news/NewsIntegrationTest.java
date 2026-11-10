package kz.studhub.news;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class NewsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired NewsRepository news;
    @Test void onlyAdminsCanPublishAndCsrfIsRequired() throws Exception {
        long before=news.count();
        mvc.perform(get("/news")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/news").with(user("reader")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/news").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        assertThat(news.count()).isEqualTo(before);
    }
    @Test void publishReadFilterEditDelete() throws Exception {
        mvc.perform(post("/news").with(user("editor").roles("ADMIN")).with(csrf())
            .param("title","Курс Java").param("body","Новый учебный материал").param("category","COURSE")
            .param("link","https://example.com/java").param("eventDate","2026-12-01"))
            .andExpect(status().is3xxRedirection());
        NewsPost post=news.findTop3ByOrderByPublishedAtDescIdDesc().getFirst();
        mvc.perform(get("/news").param("category","COURSE").with(user("reader"))).andExpect(status().isOk());
        mvc.perform(get("/news/"+post.getId()).with(user("reader"))).andExpect(status().isOk());
        mvc.perform(get("/news/new").with(user("editor").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(post("/news/"+post.getId()).with(user("reader")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/news/"+post.getId()).with(user("editor").roles("ADMIN")).with(csrf())
            .param("title","Обновлённый курс").param("body","Изменения").param("category","UPDATE"))
            .andExpect(status().is3xxRedirection());
        assertThat(news.findById(post.getId()).orElseThrow().getTitle()).isEqualTo("Обновлённый курс");
        mvc.perform(post("/news/"+post.getId()+"/delete").with(user("reader")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/news/"+post.getId()+"/delete").with(user("editor").roles("ADMIN")).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(news.existsById(post.getId())).isFalse();
    }
    @Test void rejectsUnsafeLinksAndBlankContent() throws Exception {
        long before=news.count();
        for(String link : new String[]{"javascript:alert(1)","//evil.example","/courses/1/../../logout"}) {
            mvc.perform(post("/news").with(user("editor").roles("ADMIN")).with(csrf())
                .param("title","Title").param("body","Text").param("category","USEFUL").param("link",link))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("newsForm"));
        }
        assertThat(news.count()).isEqualTo(before);
    }
}
