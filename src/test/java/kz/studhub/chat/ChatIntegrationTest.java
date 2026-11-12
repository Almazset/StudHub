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
    @Test void messageEditsAndSearchRespectRoomAndAuthor() throws Exception {
        var room=chat.direct(alice.getEmail(),bob.getId());
        var message=chat.send(alice.getEmail(),room.id(),"Первый текст",UUID.randomUUID());
        String path="/api/chat/rooms/"+room.id()+"/messages/"+message.id();
        String body=json.writeValueAsString(Map.of("text","Новый учебный текст"));
        mvc.perform(put(path).with(user(bob.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put(path).with(user(eve.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(put(path).with(user(alice.getEmail())).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put(path).with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.editedAt").exists());
        mvc.perform(get("/api/chat/rooms/"+room.id()+"/search").param("q","учебный").with(user(bob.getEmail())))
          .andExpect(status().isOk()).andExpect(jsonPath("$[0].text").value("Новый учебный текст"));
        mvc.perform(get("/api/chat/rooms/"+room.id()+"/search").param("q","учебный").with(user(eve.getEmail()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/chat/rooms/"+room.id()+"/search").param("q","%").with(user(bob.getEmail()))).andExpect(status().isBadRequest());
    }
    @Test void presenceIsPrivateAndPreferencesAreValidated() throws Exception {
        mvc.perform(post("/api/friends/presence").with(user(bob.getEmail()))).andExpect(status().isForbidden());
        mvc.perform(put("/api/friends/presence").with(user(bob.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"BAD\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/friends/presence").with(user(bob.getEmail())).with(csrf())).andExpect(status().isOk());
        var result=mvc.perform(post("/api/friends").with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("userId",bob.getId())))).andExpect(status().isOk()).andReturn();
        long id=json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/friends").with(user(alice.getEmail()))).andExpect(jsonPath("$[0].presence").value("HIDDEN"));
        mvc.perform(post("/api/friends/"+id+"/accept").with(user(bob.getEmail())).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/friends").with(user(alice.getEmail()))).andExpect(jsonPath("$[0].presence").value("ONLINE"));
        mvc.perform(put("/api/friends/presence").with(user(bob.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"INVISIBLE\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/friends/presence").with(user(bob.getEmail()))).andExpect(jsonPath("$.status").value("INVISIBLE"));
        mvc.perform(get("/api/friends").with(user(alice.getEmail()))).andExpect(jsonPath("$[0].presence").value("OFFLINE"));
        mvc.perform(get("/api/friends").with(user(eve.getEmail()))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/api/friends/"+id).with(user(alice.getEmail())).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/friends").with(user(alice.getEmail()))).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void projectManagementKeepsTasksAndChatConsistent() throws Exception {
        var created=mvc.perform(post("/api/projects").with(user(alice.getEmail())).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Team\"}"))
            .andExpect(status().isOk()).andReturn();
        long id=json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String base="/api/projects/"+id;
        mvc.perform(post(base+"/members").with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("userId",bob.getId())))).andExpect(status().isOk());
        var opened=mvc.perform(post(base+"/chat").with(user(bob.getEmail())).with(csrf())).andExpect(status().isOk()).andReturn();
        String room=json.readTree(opened.getResponse().getContentAsString()).get("id").asText();
        String details=json.writeValueAsString(Map.of("title","Updated team","description","Plan"));
        mvc.perform(put(base).with(user(bob.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(details)).andExpect(status().isNotFound());
        mvc.perform(put(base).with(user(alice.getEmail())).contentType(MediaType.APPLICATION_JSON).content(details)).andExpect(status().isForbidden());
        mvc.perform(put(base).with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(details)).andExpect(status().isOk());
        assertThat(chat.detail(bob.getEmail(),room).room().name()).isEqualTo("Updated team");
        var task=mvc.perform(post(base+"/tasks").with(user(bob.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("title","Draft","assigneeId",bob.getId())))).andExpect(status().isOk()).andReturn();
        long taskId=json.readTree(task.getResponse().getContentAsString()).get("id").asLong();
        String taskUrl=base+"/tasks/"+taskId;
        String edit=json.writeValueAsString(Map.of("title","Write tests","assigneeId",bob.getId(),"dueDate","2027-01-15"));
        mvc.perform(put(taskUrl).with(user(eve.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(edit)).andExpect(status().isNotFound());
        mvc.perform(put(taskUrl).with(user(bob.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(edit)).andExpect(status().isOk()).andExpect(jsonPath("$.dueDate").value("2027-01-15"));
        mvc.perform(put(taskUrl).with(user(bob.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("title","Wrong","assigneeId",eve.getId())))).andExpect(status().isBadRequest());
        mvc.perform(put(taskUrl).with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"   \"}")).andExpect(status().isBadRequest());
        mvc.perform(delete(taskUrl).with(user(bob.getEmail())).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete(base+"/members/"+alice.getId()).with(user(alice.getEmail())).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(delete(base+"/members/"+bob.getId()).with(user(eve.getEmail())).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete(base+"/members/"+bob.getId()).with(user(alice.getEmail())).with(csrf())).andExpect(status().isOk());
        mvc.perform(get(base+"/tasks").with(user(alice.getEmail()))).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].assigneeId").isEmpty()).andExpect(jsonPath("$[0].title").value("Write tests"));
        mvc.perform(get(base).with(user(bob.getEmail()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/chat/rooms/"+room).with(user(bob.getEmail()))).andExpect(status().isNotFound());
        mvc.perform(post("/api/chat/rooms/"+room+"/messages").with(user(bob.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("text","denied","clientId",UUID.randomUUID())))).andExpect(status().isNotFound());
        mvc.perform(get("/api/chat/rooms").with(user(bob.getEmail()))).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(room))));
        mvc.perform(delete(taskUrl).with(user(alice.getEmail())).with(csrf())).andExpect(status().isOk());
        mvc.perform(get(base+"/tasks").with(user(alice.getEmail()))).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void projectChatIsSharedAndMembershipFollowsProject() throws Exception {
        var created=mvc.perform(post("/api/projects").with(user(alice.getEmail())).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("title","Командный проект"))))
            .andExpect(status().isOk()).andReturn();
        long id=json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String endpoint="/api/projects/"+id+"/chat";
        mvc.perform(post(endpoint).with(user(alice.getEmail()))).andExpect(status().isForbidden());
        mvc.perform(post(endpoint).with(user(eve.getEmail())).with(csrf())).andExpect(status().isNotFound());
        var opened=mvc.perform(post(endpoint).with(user(alice.getEmail())).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("PROJECT"))
            .andExpect(jsonPath("$.memberCount").value(1)).andReturn();
        String room=json.readTree(opened.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/api/projects/"+id+"/members").with(user(alice.getEmail())).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("userId",bob.getId()))))
            .andExpect(status().isOk());
        // Adding a teammate updates an existing room without requiring them to open the project first.
        assertThat(chat.detail(bob.getEmail(),room).members()).hasSize(2);
        bob.setProfileVisibility("private"); users.saveAndFlush(bob);
        mvc.perform(post(endpoint).with(user(bob.getEmail())).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(room))
            .andExpect(jsonPath("$.memberCount").value(2));
        chat.send(bob.getEmail(),room,"Обсудим задачи",UUID.randomUUID());
        assertThat(chat.history(alice.getEmail(),room,null,null).messages()).hasSize(1);
        mvc.perform(get("/api/chat/rooms/"+room+"/messages").with(user(eve.getEmail())))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/chat/rooms/"+room+"/members").with(user(alice.getEmail())).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("userIds",List.of(eve.getId())))))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/chat/rooms/"+room+"/leave").with(user(bob.getEmail())).with(csrf()))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/projects/"+id).with(user(bob.getEmail())))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("data-project-chat")));
    }
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
        mvc.perform(get("/api/projects/"+id).with(user(alice.getEmail()))).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Учебный проект"));
        mvc.perform(get("/api/projects/"+id).with(user(bob.getEmail()))).andExpect(status().isOk()).andExpect(jsonPath("$.members.length()").value(2));
        mvc.perform(get("/api/projects/"+id).with(user(eve.getEmail()))).andExpect(status().isNotFound());
        mvc.perform(get("/projects/"+id).with(user(bob.getEmail()))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Учебный проект")));
        mvc.perform(get("/projects/"+id).with(user(eve.getEmail()))).andExpect(status().isNotFound());
        mvc.perform(get("/projects/"+id)).andExpect(status().is3xxRedirection());
        eve.setProfileVisibility("private"); users.saveAndFlush(eve);
        mvc.perform(post("/api/projects/"+id+"/members").with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("userId",eve.getId())))).andExpect(status().isNotFound());
    }
    @Test void projectTasksEnforceMembershipAndValidateAssignee() throws Exception {
        var created=mvc.perform(post("/api/projects").with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Project\"}")) .andExpect(status().isOk()).andReturn();
        long project=json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String base="/api/projects/"+project+"/tasks";
        mvc.perform(get(base).with(user(eve.getEmail()))).andExpect(status().isNotFound());
        mvc.perform(post(base).with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Task\",\"assigneeId\":"+eve.getId()+"}")).andExpect(status().isBadRequest());
        var task=mvc.perform(post(base).with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Prepare\",\"assigneeId\":"+alice.getId()+",\"dueDate\":\"2026-12-01\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("TODO")).andReturn();
        long taskId=json.readTree(task.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(patch(base+"/"+taskId).with(user(eve.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}")).andExpect(status().isNotFound());
        mvc.perform(patch(base+"/"+taskId).with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INVALID\"}")).andExpect(status().isBadRequest());
        mvc.perform(patch(base+"/"+taskId).with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
        mvc.perform(get(base).with(user(alice.getEmail()))).andExpect(jsonPath("$[0].title").value("Prepare"));
    }
    @Test void exactPublicIdSearchRespectsPrivacyAndRejectsInvalidIds() throws Exception {
        String id="SH-"+alice.getId();
        mvc.perform(get("/api/chat/users").param("q",id.toLowerCase()).with(user(bob.getEmail())))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(alice.getId()));
        mvc.perform(get("/api/chat/users").param("q","SH-9999999999999999999").with(user(bob.getEmail())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/chat/users").param("q",id).with(user(alice.getEmail())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        alice.setProfileVisibility("private"); users.saveAndFlush(alice);
        mvc.perform(get("/api/chat/users").param("q",id).with(user(bob.getEmail())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void friendRequestsRequireRecipientAndMembership() throws Exception {
        String body=json.writeValueAsString(Map.of("userId",bob.getId()));
        var created=mvc.perform(post("/api/friends").with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING")).andReturn();
        long id=json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/friends").with(user(bob.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("userId",alice.getId())))).andExpect(status().isConflict());
        mvc.perform(post("/api/friends/"+id+"/accept").with(user(eve.getEmail())).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/friends/"+id+"/accept").with(user(alice.getEmail())).with(csrf())).andExpect(status().isConflict());
        mvc.perform(post("/api/friends/"+id+"/accept").with(user(bob.getEmail())).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
        mvc.perform(get("/api/friends").with(user(alice.getEmail()))).andExpect(jsonPath("$[0].userId").value(bob.getId()));
        mvc.perform(post("/api/friends/"+id+"/chat").with(user(bob.getEmail())).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("DIRECT"));
        mvc.perform(delete("/api/friends/"+id).with(user(eve.getEmail())).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/friends/"+id).with(user(alice.getEmail())).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/friends").with(user(bob.getEmail()))).andExpect(jsonPath("$.length()").value(0));
        eve.setProfileVisibility("private");users.saveAndFlush(eve);
        mvc.perform(post("/api/friends").with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("userId",eve.getId())))).andExpect(status().isNotFound());
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
