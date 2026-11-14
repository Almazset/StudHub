package kz.studhub.web;

import kz.studhub.model.*;
import kz.studhub.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AssignmentOwnershipTest {
 @Autowired MockMvc mvc;
 @Autowired jakarta.persistence.EntityManager entityManager;
 @Autowired UserAccountRepository users;
 @Autowired AssignmentRepository tasks;
 @Autowired CourseRepository courses;
 UserAccount alice,bob; Course course; Assignment own,foreign,legacy;
 UserAccount account(String name){var u=new UserAccount();u.setEmail(name+"@ownership.invalid");u.setFullName(name);u.setPassword("not-a-real-login");return users.saveAndFlush(u);}
 Assignment task(String title,UserAccount owner){var a=new Assignment();a.setTitle(title);a.setDueDate(LocalDate.now().plusDays(2));a.setCourse(course);a.setStudent(owner);return tasks.saveAndFlush(a);}
 @BeforeEach void setup(){alice=account("Alice");bob=account("Bob");course=new Course();course.setTitle("Ownership course");course.setTeacherName("Teacher");courses.saveAndFlush(course);own=task("ALICE_PRIVATE",alice);foreign=task("BOB_PRIVATE",bob);legacy=task("LEGACY_UNASSIGNED",null);}
 String json(String title){return "{\"title\":\""+title+"\",\"dueDate\":\"2027-01-01\",\"courseId\":"+course.getId()+",\"completed\":true}";}
 @Test void listsDashboardCourseAndAlertsDoNotLeak() throws Exception {
  for(String path:new String[]{"/","/assignments","/courses/"+course.getId(),"/api/assignments"})
   mvc.perform(get(path).with(user(alice.getEmail()))).andExpect(status().isOk()).andExpect(content().string(containsString("ALICE_PRIVATE"))).andExpect(content().string(not(containsString("BOB_PRIVATE")))).andExpect(content().string(not(containsString("LEGACY_UNASSIGNED"))));
  mvc.perform(get("/api/assignments").with(user(bob.getEmail()))).andExpect(jsonPath("$",hasSize(1))).andExpect(jsonPath("$[0].title").value("BOB_PRIVATE"));
 }
 @Test void foreignAndUnassignedTasksAreNotAccessibleEvenToAdmin() throws Exception {
  for(var target:new Assignment[]{foreign,legacy}){
   String id=target.getId().toString();
   mvc.perform(get("/api/assignments/"+id).with(user(alice.getEmail()).roles("ADMIN"))).andExpect(status().isNotFound());
   mvc.perform(get("/assignments/"+id+"/edit").with(user(alice.getEmail()))).andExpect(status().isNotFound());
   mvc.perform(put("/api/assignments/"+id).with(user(alice.getEmail())).with(csrf()).contentType("application/json").content(json("Stolen task"))).andExpect(status().isNotFound());
   mvc.perform(delete("/api/assignments/"+id).with(user(alice.getEmail())).with(csrf())).andExpect(status().isNotFound());
   mvc.perform(post("/assignments/"+id).with(user(alice.getEmail())).with(csrf()).param("title","Stolen task").param("dueDate","2027-01-01").param("courseId",course.getId().toString())).andExpect(status().isNotFound());
   for(String action:new String[]{"toggle-completed","delete"})mvc.perform(post("/assignments/"+id+"/"+action).with(user(alice.getEmail())).with(csrf())).andExpect(status().isNotFound());
   assertThat(tasks.findById(target.getId())).isPresent();assertThat(target.isCompleted()).isFalse();
  }
 }
 @Test void createIgnoresForgedOwnerAndId() throws Exception {
  mvc.perform(post("/assignments").with(user(alice.getEmail())).with(csrf()).param("title","New private task").param("dueDate","2027-01-01").param("courseId",course.getId().toString()).param("id",foreign.getId().toString()).param("student.id",bob.getId().toString())).andExpect(status().is3xxRedirection());
  var created=tasks.findAll().stream().filter(a->a.getTitle().equals("New private task")).findFirst().orElseThrow();
  assertThat(created.getStudent().getId()).isEqualTo(alice.getId());assertThat(created.getId()).isNotEqualTo(foreign.getId());assertThat(foreign.getTitle()).isEqualTo("BOB_PRIVATE");
  mvc.perform(post("/api/assignments").with(user(bob.getEmail())).with(csrf()).contentType("application/json").content(json("Bob API task"))).andExpect(status().isOk());
  assertThat(tasks.findAll().stream().filter(a->a.getTitle().equals("Bob API task")).findFirst().orElseThrow().getStudent().getId()).isEqualTo(bob.getId());
 }
 @Test void ownerCanReadEditToggleAndDelete() throws Exception {
  String id=own.getId().toString();
  mvc.perform(get("/assignments/"+id+"/edit").with(user(alice.getEmail()))).andExpect(status().isOk())
   .andExpect(content().string(containsString("value=\"" + own.getDueDate() + "\"")));
  mvc.perform(put("/api/assignments/"+id).with(user(alice.getEmail())).with(csrf()).contentType("application/json").content(json("Updated private task"))).andExpect(status().isOk());
  assertThat(own.getTitle()).isEqualTo("Updated private task");assertThat(own.getStudent().getId()).isEqualTo(alice.getId());
  mvc.perform(post("/assignments/"+id+"/toggle-completed").with(user(alice.getEmail())).with(csrf())).andExpect(status().is3xxRedirection());assertThat(own.isCompleted()).isFalse();
  mvc.perform(delete("/api/assignments/"+id).with(user(alice.getEmail())).with(csrf())).andExpect(status().isOk());assertThat(tasks.existsById(own.getId())).isFalse();
 }
 @Test void apiSerializesReloadedCourseWithoutPersistenceOrAccountFields() throws Exception {
  Long id=own.getId();
  entityManager.flush(); entityManager.clear();
  mvc.perform(get("/api/assignments/"+id).with(user(alice.getEmail()))).andExpect(status().isOk())
   .andExpect(jsonPath("$.course.title").value("Ownership course"))
   .andExpect(jsonPath("$.course.hibernateLazyInitializer").doesNotExist())
   .andExpect(jsonPath("$.student").doesNotExist());
 }
 @Test void csrfAndApiValidationRemainEnforced() throws Exception {
  mvc.perform(delete("/api/assignments/"+own.getId()).with(user(alice.getEmail()))).andExpect(status().isForbidden());
  mvc.perform(post("/api/assignments").with(user(alice.getEmail())).with(csrf()).contentType("application/json").content(json("x"))).andExpect(status().isBadRequest());
 }
}
