package kz.studhub.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:chat-integration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "studhub.ai.gemini-api-key=", "studhub.security.key-file=target/chat-test-security.key"
})
@AutoConfigureMockMvc
class ChatIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserAccountRepository users;
    @Autowired ChatService chat;
    UserAccount alice,bob,eve;
    UserAccount account(String name) {
        var user=new UserAccount();user.setFullName(name);user.setEmail(UUID.randomUUID()+"@example.com");user.setPassword("test-hash-not-a-login");user.setProfileVisibility("members");return users.saveAndFlush(user);
    }
    @BeforeEach void setup(){alice=account("Chat Alice");bob=account("Chat Bob");eve=account("Chat Eve");}
    @Test void projectsCanBeCreatedAndSharedOnlyByTheirOwner() throws Exception {
        var result=mvc.perform(post("/api/projects").with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("title","Учебный проект","description","Описание"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.members.length()").value(1)).andReturn();
        long id=json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        String member=json.writeValueAsString(Map.of("userId",bob.getId()));
        mvc.perform(post("/api/projects/"+id+"/members").with(user(eve.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(member)).andExpect(status().isNotFound());
        mvc.perform(post("/api/projects/"+id+"/members").with(user(alice.getEmail())).contentType(MediaType.APPLICATION_JSON).content(member)).andExpect(status().isForbidden());
        mvc.perform(post("/api/projects/"+id+"/members").with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(member)).andExpect(status().isOk()).andExpect(jsonPath("$.members.length()").value(2));
        mvc.perform(get("/api/projects").with(user(bob.getEmail()))).andExpect(jsonPath("$[0].title").value("Учебный проект"));
        mvc.perform(get("/api/projects").with(user(eve.getEmail()))).andExpect(jsonPath("$.length()").value(0));
        eve.setProfileVisibility("private"); users.saveAndFlush(eve);
        mvc.perform(post("/api/projects/"+id+"/members").with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("userId",eve.getId())))).andExpect(status().isNotFound());
    }
    @Test void nicknameIsSavedSearchableAndRespectsProfilePrivacy() throws Exception {
        mvc.perform(put("/api/settings/profile").with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("name",alice.getFullName(),"email",alice.getEmail(),"nickname"," donk "))))
            .andExpect(status().isOk());
        mvc.perform(get("/api/settings").with(user(alice.getEmail()))).andExpect(jsonPath("$.profile.nickname").value("donk"));
        mvc.perform(get("/api/chat/users").param("q","donk").with(user(bob.getEmail())))
            .andExpect(jsonPath("$[0].name").value("donk"));
        alice=users.findById(alice.getId()).orElseThrow(); alice.setProfileVisibility("private"); users.saveAndFlush(alice);
        mvc.perform(get("/api/chat/users").param("q","donk").with(user(bob.getEmail())))
            .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/api/settings/profile").with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("name",alice.getFullName(),"email",alice.getEmail(),"nickname",""))))
            .andExpect(status().isOk());
        assertThat(users.findById(alice.getId()).orElseThrow().getDisplayName()).isEqualTo("Chat Alice");
    }
    @Test void twoAccountsExchangePersistedMessagesThroughSecuredApi() throws Exception {
        mvc.perform(get("/chat").with(user(alice.getEmail()))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-chat-root")));
        var created=mvc.perform(post("/api/chat/direct").with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("userId",bob.getId())))).andExpect(status().isOk()).andReturn();
        String id=json.readTree(created.getResponse().getContentAsString()).get("id").asText();
        var payload=json.writeValueAsString(Map.of("text","Lecture <script>alert(1)</script> https://example.com","clientId",UUID.randomUUID()));
        var sent=mvc.perform(post("/api/chat/rooms/"+id+"/messages").with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isOk()).andExpect(jsonPath("$.sender.id").value(alice.getId())).andExpect(jsonPath("$.sender.email").doesNotExist()).andReturn();
        long messageId=json.readTree(sent.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/chat/rooms/"+id+"/messages").with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(messageId));
        mvc.perform(get("/api/chat/rooms/"+id+"/messages").with(user(bob.getEmail()))).andExpect(status().isOk())
            .andExpect(jsonPath("$.messages.length()").value(1)).andExpect(jsonPath("$.messages[0].id").value(messageId));
        mvc.perform(get("/api/chat/rooms/"+id+"/messages").with(user(eve.getEmail()).roles("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/chat/rooms/"+id+"/read").with(user(bob.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("messageId",messageId)))).andExpect(status().isOk());
        mvc.perform(get("/api/chat/rooms/"+id).with(user(bob.getEmail()))).andExpect(jsonPath("$.room.unread").value(0));
        mvc.perform(get("/api/chat/users").param("q","Chat Alice").with(user(bob.getEmail()))).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].email").doesNotExist()).andExpect(jsonPath("$[0].password").doesNotExist());
    }
    @Test void concurrentDirectCreationAndRetryProduceOneRoomAndOneMessage() throws Exception {
        try(var executor=Executors.newFixedThreadPool(4)) {
            var start=new CountDownLatch(1);
            List<Callable<String>> requests=List.of(
                ()->{start.await();return chat.direct(alice.getEmail(),bob.getId()).id();},
                ()->{start.await();return chat.direct(bob.getEmail(),alice.getId()).id();},
                ()->{start.await();return chat.direct(alice.getEmail(),bob.getId()).id();});
            var futures=requests.stream().map(executor::submit).toList();start.countDown();
            var ids=new HashSet<String>();for(var future:futures)ids.add(future.get(10,TimeUnit.SECONDS));assertThat(ids).hasSize(1);
            String room=ids.iterator().next();UUID token=UUID.randomUUID();
            var first=executor.submit(()->chat.send(alice.getEmail(),room,"Retry me",token));
            var second=executor.submit(()->chat.send(alice.getEmail(),room,"Retry me",token));
            assertThat(first.get(10,TimeUnit.SECONDS).id()).isEqualTo(second.get(10,TimeUnit.SECONDS).id());
            assertThat(chat.history(bob.getEmail(),room,null,null).messages()).hasSize(1);
        }
    }
}
