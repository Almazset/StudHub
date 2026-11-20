package kz.studhub.chat;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    @Autowired PlatformTransactionManager transactions;
    @Autowired kz.studhub.repository.CourseRepository courseRepository;
    UserAccount alice,bob,eve;
    UserAccount account(String name) {
        var user=new UserAccount();user.setFullName(name);user.setEmail(UUID.randomUUID()+"@example.com");user.setPassword("test-hash-not-a-login");user.setProfileVisibility("members");return users.saveAndFlush(user);
    }
    @BeforeEach void setup(){alice=account("Chat Alice");bob=account("Chat Bob");eve=account("Chat Eve");}
    @Test void chatNotificationsCoalesceOnlyCommittedMessagesForRoomPeers() throws Exception {
        var direct = chat.direct(alice.getEmail(), bob.getId());
        UUID clientId = UUID.randomUUID();
        var original = chat.send(alice.getEmail(), direct.id(), "Private lecture draft", clientId);
        JsonNode first = notifications(bob);
        assertThat(first.path("unread").asInt()).isEqualTo(1);
        JsonNode alert = first.path("items").get(0);
        long alertId = alert.path("id").asLong();
        assertThat(alert.path("type").asText()).isEqualTo("CHAT_MESSAGE");
        assertThat(alert.path("href").asText()).isEqualTo("/chat?room=" + direct.id());
        assertThat(first.toString()).doesNotContain(original.text(), alice.getEmail());
        assertThat(notificationTypes(alice)).isEmpty();
        assertThat(notificationTypes(eve)).isEmpty();

        mvc.perform(post("/api/notifications/" + alertId + "/read").with(user(bob.getEmail())).with(csrf()))
            .andExpect(status().isNoContent());
        chat.send(alice.getEmail(), direct.id(), "Private lecture draft", clientId);
        assertThat(notifications(bob).path("unread").asInt()).isZero();

        chat.send(alice.getEmail(), direct.id(), "Follow-up", UUID.randomUUID());
        JsonNode refreshed = notifications(bob);
        assertThat(refreshed.path("unread").asInt()).isEqualTo(1);
        assertThat(refreshed.path("items").size()).isEqualTo(1);
        assertThat(refreshed.path("items").get(0).path("id").asLong()).isEqualTo(alertId);

        var group = chat.group(alice.getEmail(), "Study group", Set.of(bob.getId(), eve.getId()));
        chat.forward(alice.getEmail(), direct.id(), original.id(), group.id(), UUID.randomUUID());
        chat.send(alice.getEmail(), "global", "Public message", UUID.randomUUID());
        assertThat(notificationTypes(alice)).isEmpty();
        assertThat(notificationTypes(bob)).containsExactly("CHAT_MESSAGE", "CHAT_MESSAGE");
        assertThat(notificationTypes(eve)).containsExactly("CHAT_MESSAGE");

        var rolledBack = chat.direct(alice.getEmail(), eve.getId());
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            chat.send(alice.getEmail(), rolledBack.id(), "Discarded draft", UUID.randomUUID());
            tx.setRollbackOnly();
        });
        assertThat(chat.history(eve.getEmail(), rolledBack.id(), null, null).messages()).isEmpty();
        assertThat(notificationTypes(eve)).containsExactly("CHAT_MESSAGE");
    }

    @Test void friendRequestsInvitationsAndTaskAssignmentsNotifyOnlyOnNewTransitions() throws Exception {
        var invitation = Map.of("userId", bob.getId());
        action(post("/api/friends"), alice, invitation, 200);
        action(post("/api/friends"), alice, invitation, 409);
        assertThat(notificationTypes(bob)).containsExactly("FRIEND_REQUEST");

        long project = action(post("/api/projects"), alice, Map.of("title", "Notification test"), 200)
            .path("id").asLong();
        String path = "/api/projects/" + project;
        action(post(path + "/members"), alice, invitation, 200);
        action(post(path + "/members"), alice, invitation, 200);
        assertThat(notificationTypes(bob)).containsExactlyInAnyOrder("PROJECT_INVITATION", "FRIEND_REQUEST");
        action(post(path + "/invitation/accept"), bob, null, 200);
        long task = action(post(path + "/tasks"), alice, Map.of("title", "Initial task", "assigneeId", bob.getId()), 200)
            .path("id").asLong();
        String taskPath = path + "/tasks/" + task;
        action(put(taskPath), alice, Map.of("title", "Edited task", "assigneeId", bob.getId()), 200);
        action(put(taskPath), alice, Map.of("title", "Unauthorized assignee", "assigneeId", eve.getId()), 400);

        assertThat(notificationTypes(bob)).containsExactlyInAnyOrder("FRIEND_REQUEST", "PROJECT_INVITATION", "TASK_ASSIGNED");
        assertThat(notificationTypes(eve)).isEmpty();
        assertThat(notificationTypes(alice)).isEmpty();

        action(post(path + "/members"), alice, Map.of("userId", eve.getId()), 200);
        action(post(path + "/invitation/accept"), eve, null, 200);
        action(put(taskPath), alice, Map.of("title", "Reassigned task", "assigneeId", eve.getId()), 200);
        assertThat(notificationTypes(eve)).containsExactlyInAnyOrder("PROJECT_INVITATION", "TASK_ASSIGNED");
        assertThat(notificationTypes(bob)).containsExactlyInAnyOrder("FRIEND_REQUEST", "PROJECT_INVITATION", "TASK_ASSIGNED");

        action(put(taskPath), alice, Map.of("title", "Returned task", "assigneeId", bob.getId()), 200);
        assertThat(notificationTypes(bob))
            .containsExactlyInAnyOrder("FRIEND_REQUEST", "PROJECT_INVITATION", "TASK_ASSIGNED", "TASK_ASSIGNED");
    }

    private JsonNode action(MockHttpServletRequestBuilder request, UserAccount actor, Map<String, ?> body,
                            int expectedStatus) throws Exception {
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        var response = mvc.perform(request.with(user(actor.getEmail())).with(csrf()))
            .andExpect(status().is(expectedStatus)).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray());
    }

    private JsonNode notifications(UserAccount recipient) throws Exception {
        return action(get("/api/notifications"), recipient, null, 200);
    }

    private List<String> notificationTypes(UserAccount recipient) throws Exception {
        List<String> types = new ArrayList<>();
        notifications(recipient).path("items").forEach(item -> types.add(item.path("type").asText()));
        return types;
    }

    @Test void courseProgressIsPersonalAndValidatesOwnership() throws Exception {
        var course=new kz.studhub.model.Course();course.setTitle("Study course");course.setTeacherName("Teacher");course=courseRepository.saveAndFlush(course);
        String base="/api/courses/"+course.getId()+"/study-steps";
        mvc.perform(post(base).with(user(alice.getEmail())).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Lesson\"}")).andExpect(status().isForbidden());
        mvc.perform(post(base).with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  \"}")).andExpect(status().isBadRequest());
        var created=mvc.perform(post(base).with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Lesson\"}")).andExpect(status().isOk()).andReturn();
        long id=json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get(base).with(user(bob.getEmail()))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put(base+"/"+id).with(user(bob.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}")).andExpect(status().isNotFound());
        mvc.perform(delete(base+"/"+id).with(user(bob.getEmail())).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(put(base+"/"+id).with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(true));
        mvc.perform(get(base).with(user(alice.getEmail()))).andExpect(jsonPath("$[0].completed").value(true));
        mvc.perform(delete(base+"/"+id).with(user(alice.getEmail())).with(csrf())).andExpect(status().isOk());
        mvc.perform(get(base).with(user(alice.getEmail()))).andExpect(jsonPath("$.length()").value(0));
    }
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
        mvc.perform(post(base+"/invitation/accept").with(user(bob.getEmail())).with(csrf())).andExpect(status().isOk());
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
        mvc.perform(post("/api/projects/"+id+"/invitation/accept").with(user(bob.getEmail())).with(csrf())).andExpect(status().isOk());
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
        mvc.perform(post("/api/projects/"+id+"/members").with(user(alice.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(member)).andExpect(status().isOk()).andExpect(jsonPath("$.members.length()").value(1));
        mvc.perform(post("/api/projects/"+id+"/invitation/accept").with(user(bob.getEmail())).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.members.length()").value(2));
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
    @Test void recipientRuleAppliesToExistingChatsAndEveryWayToSend() throws Exception {
        var room = chat.direct(alice.getEmail(), bob.getId());
        String path = "/api/chat/rooms/" + room.id() + "/messages";
        var existing = chat.send(bob.getEmail(), room.id(), "Older message", UUID.randomUUID());
        var outgoing = Map.of("text", "New message", "clientId", UUID.randomUUID());

        bob.setDirectMessages("friends");
        users.saveAndFlush(bob);
        assertThat(action(post("/api/chat/direct"), alice, Map.of("userId", bob.getId()), 403)
            .path("error").asText()).isEqualTo("DIRECT_UNAVAILABLE");
        assertThat(action(post(path), alice, outgoing, 403).path("error").asText()).isEqualTo("DIRECT_UNAVAILABLE");

        long requestId = action(post("/api/friends"), alice, Map.of("userId", bob.getId()), 200).path("id").asLong();
        action(post("/api/friends/" + requestId + "/accept"), bob, null, 200);
        action(post(path), alice, outgoing, 200);

        bob = users.findById(bob.getId()).orElseThrow();
        bob.setDirectMessages("nobody");
        users.saveAndFlush(bob);
        mvc.perform(get("/api/chat/rooms/" + room.id()).with(user(alice.getEmail())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.room.canSend").value(false));
        assertThat(action(post(path), alice, Map.of("text", "Denied", "clientId", UUID.randomUUID()), 403)
            .path("error").asText()).isEqualTo("DIRECT_UNAVAILABLE");
        var meta = new MockMultipartFile("message", "", "application/json", json.writeValueAsBytes(outgoing));
        var file = new MockMultipartFile("file", "lecture.png", "image/png", ChatFileValidatorTest.image("png"));
        mvc.perform(multipart(path).file(meta).file(file).with(user(alice.getEmail())).with(csrf()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("DIRECT_UNAVAILABLE"));
        action(post(path + "/" + existing.id() + "/forward"), alice,
            Map.of("roomId", room.id(), "clientId", UUID.randomUUID()), 403);

        // Closing incoming messages for Bob keeps Bob's ability to write to Alice.
        action(post(path), bob, outgoing, 200);
        assertThat(chat.history(alice.getEmail(), room.id(), null, null).messages())
            .extracting(ChatService.Message::text).containsExactly("Older message", "New message", "New message");
    }

    @Test void blockingClosesBothDirectionsAndConcealsDirectoryResults() throws Exception {
        var direct = chat.direct(alice.getEmail(), bob.getId());
        var group = chat.group(alice.getEmail(), "Joint study", Set.of(bob.getId(), eve.getId()));
        var earlier = chat.send(bob.getEmail(), direct.id(), "Existing history", UUID.randomUUID());

        var shId = "SH-" + bob.getId();
        var visible = action(get("/api/chat/users").param("q", shId), alice, null, 200).get(0);
        assertThat(visible.propertyStream().map(Map.Entry::getKey)).containsExactlyInAnyOrder("id", "name");
        action(put("/api/friends/blocked/" + alice.getId()), bob, null, 204);
        for (var actor : List.of(alice, bob)) {
            var peer = actor.equals(alice) ? bob : alice;
            String path = "/api/chat/rooms/" + direct.id() + "/messages";
            var hidden = action(get("/api/chat/users").param("q", "SH-" + peer.getId()), actor, null, 200);
            var unknown = action(get("/api/chat/users").param("q", "SH-" + Long.MAX_VALUE), actor, null, 200);
            assertThat(hidden).isEqualTo(unknown);
            assertThat(action(get("/api/chat/users").param("q", peer.getFullName()), actor, null, 200))
                .noneMatch(result -> result.path("id").asLong() == peer.getId());
            assertThat(action(post("/api/chat/direct"), actor, Map.of("userId", peer.getId()), 403)
                .path("error").asText()).isEqualTo("DIRECT_UNAVAILABLE");
            action(post(path), actor, Map.of("text", "Denied", "clientId", UUID.randomUUID()), 403);
            action(put(path + "/" + earlier.id() + "/reaction"), actor, Map.of("emoji", "👍"), 403);
            mvc.perform(get("/api/chat/rooms/" + direct.id()).with(user(actor.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.room.canSend").value(false))
                .andExpect(jsonPath("$.room.peerId").value(peer.getId()))
                .andExpect(jsonPath("$.room.name").doesNotExist());
        }
        assertThat(chat.history(alice.getEmail(), direct.id(), null, null).messages())
            .extracting(ChatService.Message::text).containsExactly("Existing history");

        chat.send(alice.getEmail(), group.id(), "Group conversation", UUID.randomUUID());
        assertThat(chat.history(bob.getEmail(), group.id(), null, null).messages()).hasSize(1);
        assertThat(notificationTypes(bob)).doesNotContain("CHAT_MESSAGE");
        assertThat(notificationTypes(eve)).containsExactly("CHAT_MESSAGE");

        action(delete("/api/friends/blocked/" + alice.getId()), bob, null, 204);
        assertThat(chat.detail(alice.getEmail(), direct.id()).room().canSend()).isTrue();
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

    @Test void messageActionsValidateCsrfMembershipAndInput() throws Exception {
        var room=chat.direct(alice.getEmail(),bob.getId());
        var message=chat.send(alice.getEmail(),room.id(),"Original",UUID.randomUUID());
        String base="/api/chat/rooms/"+room.id(), path=base+"/messages/"+message.id();
        for(String action:List.of("favorite","pin","reaction")) {
            String payload=action.equals("reaction")?"{\"emoji\":\"👍\"}":"{\"enabled\":true}";
            mvc.perform(put(path+"/"+action).with(user(bob.getEmail())).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isForbidden());
            mvc.perform(put(path+"/"+action).with(user(eve.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isNotFound());
            mvc.perform(put(path+"/"+action).with(user(bob.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());
        }
        mvc.perform(put(path+"/reaction").with(user(bob.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"emoji\":\"bad\"}")).andExpect(status().isBadRequest());
        mvc.perform(put(path+"/favorite").with(user(bob.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get(base+"/saved").param("kind","favorites").with(user(alice.getEmail()))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(base+"/saved").param("kind","favorites").with(user(eve.getEmail()))).andExpect(status().isNotFound());
        mvc.perform(get(base+"/around/"+message.id()).with(user(eve.getEmail()))).andExpect(status().isNotFound());
        mvc.perform(post(base+"/refresh").with(user(eve.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("ids",List.of(message.id()))))).andExpect(status().isNotFound());
        mvc.perform(post(base+"/messages").with(user(bob.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("text","Reply","clientId",UUID.randomUUID(),"replyToId",message.id())))).andExpect(status().isOk()).andExpect(jsonPath("$.reply.text").value("Original"));
    }

    @Test void attachmentsArePrivateTransactionalAndRetrySafe() throws Exception {
        String room=chat.group(alice.getEmail(),"File test",Set.of(bob.getId())).id();
        String path="/api/chat/rooms/"+room+"/messages";
        UUID token=UUID.randomUUID();
        var metadata=new MockMultipartFile("message","","application/json",
            json.writeValueAsBytes(Map.of("text","","clientId",token)));
        var file=new MockMultipartFile("file","Лекция.png","image/png",ChatFileValidatorTest.image("png"));
        mvc.perform(multipart(path).file(metadata).file(file).with(user(alice.getEmail())))
            .andExpect(status().isForbidden());
        mvc.perform(multipart(path).file(metadata).file(file).with(user(eve.getEmail())).with(csrf()))
            .andExpect(status().isNotFound());
        var malformed=new MockMultipartFile("file","notes.png","image/png",new byte[]{1,2,3});
        mvc.perform(multipart(path).file(metadata).file(malformed).with(user(alice.getEmail())).with(csrf()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("FILE_CONTENT"));
        assertThat(chat.history(alice.getEmail(),room,null,null).messages()).isEmpty();

        var sent=mvc.perform(multipart(path).file(metadata).file(file).with(user(alice.getEmail())).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.attachment.name").value("Лекция.png"))
            .andExpect(jsonPath("$.attachment.data").doesNotExist()).andReturn();
        long id=json.readTree(sent.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("id").asLong();
        mvc.perform(multipart(path).file(metadata).file(file).with(user(alice.getEmail())).with(csrf()))
            .andExpect(jsonPath("$.id").value(id));
        String download=path+"/"+id+"/attachment";
        mvc.perform(get(download).with(user(bob.getEmail())))
            .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
            .andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;")));
        mvc.perform(get(download).with(user(eve.getEmail()).roles("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/chat/rooms/global/messages/"+id+"/attachment").with(user(alice.getEmail())))
            .andExpect(status().isNotFound());

        String destination=chat.direct(bob.getEmail(),eve.getId()).id();
        var copy=chat.forward(bob.getEmail(),room,id,destination,UUID.randomUUID());
        assertThat(copy.attachment().name()).isEqualTo("Лекция.png");
        chat.leave(bob.getEmail(),room);
        mvc.perform(get(download).with(user(bob.getEmail()))).andExpect(status().isNotFound());
        mvc.perform(delete(path+"/"+id).with(user(alice.getEmail())).with(csrf())).andExpect(status().isOk());
        mvc.perform(get(download).with(user(alice.getEmail()))).andExpect(status().isNotFound());
        assertThat(chat.download(eve.getEmail(),destination,copy.id()).data()).isNotEmpty();
    }
}
