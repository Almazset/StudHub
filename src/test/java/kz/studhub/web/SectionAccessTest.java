package kz.studhub.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import kz.studhub.model.*;
import kz.studhub.repository.*;
import kz.studhub.security.DeviceSessionRegistry;
import kz.studhub.service.CourseService;
import kz.studhub.chat.ChatService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class SectionAccessTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserAccountRepository users;
    @Autowired CourseRepository courses;
    @Autowired CourseService courseService;
    @Autowired DeviceSessionRegistry registry;
    @Autowired ChatService chat;
    UserAccount alice, bob, admin;
    Course course;
    final List<String> sessions = new ArrayList<>();

    @BeforeEach void setup() {
        alice = account("alice", Role.USER);
        bob = account("bob", Role.USER);
        admin = account("admin", Role.ADMIN);
        course = new Course(); course.setTitle("Shared course"); course.setTeacherName("Teacher");
        courses.saveAndFlush(course);
    }
    UserAccount account(String name, Role role) {
        var u = new UserAccount(); u.setFullName(name); u.setEmail(UUID.randomUUID()+"@access.invalid");
        u.setPassword("private-test-hash"); u.setRole(role);
        return users.saveAndFlush(u);
    }
    String body(Object value) throws Exception { return json.writeValueAsString(value); }
    String courseBody() throws Exception { return body(Map.of("title","Changed course","teacherName","Teacher")); }
    String trackedSession(UserAccount u) {
        String id=UUID.randomUUID().toString(); sessions.add(id);
        registry.registerNewSession(id,User.withUsername(u.getEmail()).password("unused").roles(u.getRole().name()).build());
        return id;
    }
    @AfterEach void cleanupSessions() { sessions.forEach(registry::removeSessionInformation); }

    @Test void protectedSectionsRequireLogin() throws Exception {
        for (String path : List.of("/courses","/api/courses","/projects","/api/projects","/chat","/api/chat/rooms",
                "/profile","/api/profiles/"+alice.getId(),"/settings","/api/settings","/api/assistant/status","/api/users"))
            mvc.perform(get(path)).andExpect(status().is3xxRedirection());
    }

    @Test void membersReadCoursesButCannotManageThemThroughFormsOrApi() throws Exception {
        mvc.perform(get("/courses").with(user(alice.getEmail()))).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("/courses/new"))))
            .andExpect(content().string(not(containsString("data-delete-course"))));
        mvc.perform(get("/api/courses/"+course.getId()).with(user(alice.getEmail()))).andExpect(status().isOk());
        for (String path : List.of("/courses/new","/courses/"+course.getId()+"/edit"))
            mvc.perform(get(path).with(user(alice.getEmail()))).andExpect(status().isForbidden());
        for (String path : List.of("/courses","/courses/"+course.getId(),"/courses/"+course.getId()+"/delete"))
            mvc.perform(post(path).with(user(alice.getEmail())).with(csrf()).param("title","Changed").param("teacherName","Teacher"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/courses").with(user(alice.getEmail())).with(csrf()).contentType("application/json").content(courseBody()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mvc.perform(put("/api/courses/"+course.getId()).with(user(alice.getEmail())).with(csrf()).contentType("application/json").content(courseBody()))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/courses/"+course.getId()).with(user(alice.getEmail())).with(csrf())).andExpect(status().isForbidden());
        assertThat(courses.findById(course.getId()).orElseThrow().getTitle()).isEqualTo("Shared course");
    }

    @Test @WithMockUser void courseServiceAlsoEnforcesAdminBoundary() {
        assertThatThrownBy(() -> courseService.save(new Course())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> courseService.update(course.getId(),course)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> courseService.delete(course.getId())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test void adminCanManageCoursesButStillNeedsCsrf() throws Exception {
        mvc.perform(get("/courses/new").with(user(admin.getEmail()).roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(post("/api/courses").with(user(admin.getEmail()).roles("ADMIN")).contentType("application/json").content(courseBody()))
            .andExpect(status().isForbidden());
        var result=mvc.perform(post("/api/courses").with(user(admin.getEmail()).roles("ADMIN")).with(csrf()).contentType("application/json").content(courseBody()))
            .andExpect(status().isOk()).andReturn();
        long id=json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(put("/api/courses/"+id).with(user(admin.getEmail()).roles("ADMIN")).with(csrf()).contentType("application/json").content(courseBody()))
            .andExpect(status().isOk());
        mvc.perform(delete("/api/courses/"+id).with(user(admin.getEmail()).roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        assertThat(courses.existsById(id)).isFalse();
    }

    @Test void adminEndpointsAndNewsRejectOrdinaryUsers() throws Exception {
        for(String path:List.of("/api/users","/api/users/"+bob.getId(),"/users","/api/settings/support/inbox","/news/new"))
            mvc.perform(get(path).with(user(alice.getEmail()))).andExpect(status().isForbidden());
        String payload=body(Map.of("fullName","Imposter","email","bad@access.invalid","password","LongPassword123","role","ADMIN"));
        mvc.perform(post("/api/users").with(user(alice.getEmail())).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isForbidden());
        mvc.perform(put("/api/users/"+bob.getId()).with(user(alice.getEmail())).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/"+bob.getId()).with(user(alice.getEmail())).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/settings/support/1/close").with(user(alice.getEmail())).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/news/1/delete").with(user(alice.getEmail())).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/users").with(user(admin.getEmail()).roles("ADMIN"))).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("private-test-hash"))));
    }

    @Test void registrationAndSettingsCannotAssignRolesOrTargetAnotherAccount() throws Exception {
        String email="register-"+UUID.randomUUID()+"@access.invalid";
        mvc.perform(post("/register").with(csrf()).param("fullName","New member").param("email",email)
            .param("password","Password!2026").param("confirmPassword","Password!2026").param("role","ADMIN"))
            .andExpect(status().is3xxRedirection());
        assertThat(users.findByEmailIgnoreCase(email).orElseThrow().getRole()).isEqualTo(Role.USER);
        mvc.perform(put("/api/settings/privacy").with(user(alice.getEmail())).with(csrf()).contentType("application/json")
            .content(body(Map.of("visibility","members","userId",bob.getId(),"role","ADMIN")))).andExpect(status().isOk());
        assertThat(users.findById(alice.getId()).orElseThrow().getProfileVisibility()).isEqualTo("members");
        assertThat(bob.getProfileVisibility()).isEqualTo("private");
        assertThat(alice.getRole()).isEqualTo(Role.USER);
        mvc.perform(get("/api/settings").with(user(alice.getEmail())).param("userId",bob.getId().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.profile.id").value(alice.getId()));
    }

    @Test void privateProfilesAndPhotosAreHiddenEvenFromAdmin() throws Exception {
        bob.setPhoto(new byte[]{1,2,3}); users.saveAndFlush(bob);
        for(String suffix:List.of("","/photo")) {
            mvc.perform(get("/api/profiles/"+bob.getId()+suffix).with(user(alice.getEmail()))).andExpect(status().isNotFound());
            mvc.perform(get("/api/profiles/"+bob.getId()+suffix).with(user(admin.getEmail()).roles("ADMIN"))).andExpect(status().isNotFound());
            mvc.perform(get("/api/profiles/"+bob.getId()+suffix).with(user(bob.getEmail()))).andExpect(status().isOk());
        }
        bob.setProfileVisibility("members"); users.saveAndFlush(bob);
        mvc.perform(get("/api/profiles/"+bob.getId()).with(user(alice.getEmail()))).andExpect(status().isOk())
            .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.role").doesNotExist());
        mvc.perform(get("/api/profiles/"+bob.getId()+"/photo").with(user(alice.getEmail()))).andExpect(status().isOk());
    }

    @Test void roleEmailChangesAndDeletionExpireExistingSessions() throws Exception {
        String first=trackedSession(bob);
        mvc.perform(put("/api/users/"+bob.getId()).with(user(admin.getEmail()).roles("ADMIN")).with(csrf()).contentType("application/json")
            .content(body(Map.of("fullName","Bob","email",bob.getEmail(),"role","ADMIN")))).andExpect(status().isOk());
        assertThat(registry.getSessionInformation(first).isExpired()).isTrue();
        String second=trackedSession(bob);
        mvc.perform(put("/api/users/"+bob.getId()).with(user(admin.getEmail()).roles("ADMIN")).with(csrf()).contentType("application/json")
            .content(body(Map.of("fullName","Bob","email","renamed-"+bob.getId()+"@access.invalid","role","USER")))).andExpect(status().isOk());
        assertThat(registry.getSessionInformation(second).isExpired()).isTrue();
        String third=trackedSession(bob);
        mvc.perform(delete("/api/users/"+bob.getId()).with(user(admin.getEmail()).roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        assertThat(registry.getSessionInformation(third).isExpired()).isTrue();
    }

    @Test void adminIsStillAnOutsiderToProjectsAndPrivateChats() throws Exception {
        var created=mvc.perform(post("/api/projects").with(user(alice.getEmail())).with(csrf()).contentType("application/json")
            .content(body(Map.of("title","Private team")))).andExpect(status().isOk()).andReturn();
        long id=json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        for(String path:List.of("/projects/"+id,"/api/projects/"+id,"/api/projects/"+id+"/tasks"))
            mvc.perform(get(path).with(user(admin.getEmail()).roles("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/projects/"+id+"/chat").with(user(admin.getEmail()).roles("ADMIN")).with(csrf())).andExpect(status().isNotFound());
        bob.setProfileVisibility("members"); users.saveAndFlush(bob);
        var room=chat.group(alice.getEmail(),"Private group",Set.of(bob.getId()));
        var message=chat.send(alice.getEmail(),room.id(),"Private text",UUID.randomUUID());
        String base="/api/chat/rooms/"+room.id();
        for(String path:List.of(base,base+"/messages",base+"/search?q=Private"))
            mvc.perform(get(path).with(user(admin.getEmail()).roles("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(delete(base+"/messages/"+message.id()).with(user(admin.getEmail()).roles("ADMIN")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete(base+"/messages/"+message.id()).with(user(bob.getEmail())).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete(base+"/messages/"+message.id()).with(user(alice.getEmail())).with(csrf())).andExpect(status().isOk());
    }
}
