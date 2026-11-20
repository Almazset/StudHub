package kz.studhub.study;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.studhub.model.*;
import kz.studhub.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class CourseLearningTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired CourseRepository courses;@Autowired UserAccountRepository users;@Autowired CourseLessonRepository lessons;@Autowired LessonProgressRepository marks;@Autowired AssignmentRepository assignments;
 UserAccount a,b;Course course;String base;
 UserAccount userAccount(){var u=new UserAccount();u.setEmail(UUID.randomUUID()+"@lessons.invalid");u.setFullName("Student");u.setPassword("unused-hash");return users.saveAndFlush(u);}
 @BeforeEach void setup(){a=userAccount();b=userAccount();course=new Course();course.setTitle("Internal course");course.setTeacherName("Author");courses.saveAndFlush(course);base="/api/courses/"+course.getId()+"/lessons";}
 String input(String title)throws Exception{return json.writeValueAsString(Map.of("sectionTitle","Getting started","sectionOrder",1,"lessonOrder",1,"title",title,"content","<script>alert(1)</script> Plain text lesson"));}
 long create(String title)throws Exception{var r=mvc.perform(post(base).with(user(a.getEmail()).roles("ADMIN")).with(csrf()).contentType("application/json").content(input(title))).andExpect(status().isOk()).andReturn();return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();}
 @Test void onlyAdminPublishesAndWrongCourseCannotBeUsed()throws Exception{
  mvc.perform(post(base).with(user(a.getEmail())).with(csrf()).contentType("application/json").content(input("Denied"))).andExpect(status().isForbidden());
  mvc.perform(post(base).with(user(a.getEmail()).roles("ADMIN")).contentType("application/json").content(input("Denied"))).andExpect(status().isForbidden());
  long id=create("Lesson");
  mvc.perform(put(base+"/"+id).with(user(b.getEmail())).with(csrf()).contentType("application/json").content(input("Denied"))).andExpect(status().isForbidden());
  mvc.perform(delete(base+"/"+id).with(user(b.getEmail())).with(csrf())).andExpect(status().isForbidden());
  var other=new Course();other.setTitle("Other course");other.setTeacherName("Author");courses.saveAndFlush(other);
  mvc.perform(get("/api/courses/"+other.getId()+"/lessons/"+id).with(user(b.getEmail()))).andExpect(status().isNotFound());
  mvc.perform(put("/api/courses/"+other.getId()+"/lessons/"+id+"/progress").with(user(b.getEmail())).with(csrf()).contentType("application/json").content("{\"completed\":true}")).andExpect(status().isNotFound());
 }
 @Test void visitsResumeButCompletionIsManualAndPrivate()throws Exception{
  long first=create("First"),second=create("Second");
  mvc.perform(get(base).with(user(a.getEmail()))).andExpect(jsonPath("$.resumeId").value(first)).andExpect(jsonPath("$.completedCount").value(0));
  mvc.perform(post(base+"/"+second+"/visit").with(user(a.getEmail())).with(csrf())).andExpect(status().isOk());
  mvc.perform(get(base).with(user(a.getEmail()))).andExpect(jsonPath("$.resumeId").value(second)).andExpect(jsonPath("$.completedCount").value(0));
  mvc.perform(put(base+"/"+second+"/progress").with(user(a.getEmail())).with(csrf()).contentType("application/json").content("{\"completed\":true,\"userId\":"+b.getId()+"}")).andExpect(status().isOk());
  mvc.perform(get(base).with(user(a.getEmail()))).andExpect(jsonPath("$.resumeId").value(first)).andExpect(jsonPath("$.completedCount").value(1));
  mvc.perform(get(base).with(user(b.getEmail()))).andExpect(jsonPath("$.resumeId").value(first)).andExpect(jsonPath("$.completedCount").value(0));
  mvc.perform(put(base+"/"+first+"/progress").with(user(a.getEmail())).with(csrf()).contentType("application/json").content("{\"completed\":true}")).andExpect(status().isOk());
  mvc.perform(get(base).with(user(a.getEmail()))).andExpect(jsonPath("$.allCompleted").value(true));
  mvc.perform(put(base+"/"+second+"/progress").with(user(a.getEmail())).with(csrf()).contentType("application/json").content("{\"completed\":false}")).andExpect(status().isOk());
  mvc.perform(get(base).with(user(a.getEmail()))).andExpect(jsonPath("$.resumeId").value(second)).andExpect(jsonPath("$.allCompleted").value(false));
  assertThat(marks.findByUserIdAndLessonCourseId(a.getId(),course.getId())).hasSize(2);
 }
 @Test void externalCourseRejectsLocalLessonsAndShowsInstructions()throws Exception{
  course.setExternalUrl("https://example.org/course");course.setLanguage("Русский");course.setStartInstructions("Войдите на платформу автора");courses.saveAndFlush(course);
  mvc.perform(get(base).with(user(a.getEmail()))).andExpect(status().isConflict());
  mvc.perform(post(base).with(user(a.getEmail()).roles("ADMIN")).with(csrf()).contentType("application/json").content(input("No"))).andExpect(status().isConflict());
  mvc.perform(get("/courses/"+course.getId()).with(user(a.getEmail()))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Войдите на платформу автора"))).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("data-course-learning="))));
 }
 @Test void deletionClearsMarksAndEmptyCourseHasNoFakeProgress()throws Exception{
  long id=create("Remove");mvc.perform(post(base+"/"+id+"/visit").with(user(a.getEmail())).with(csrf())).andExpect(status().isOk());
  mvc.perform(delete(base+"/"+id).with(user(a.getEmail()).roles("ADMIN")).with(csrf())).andExpect(status().isOk());
  assertThat(marks.findByUserIdAndLessonCourseId(a.getId(),course.getId())).isEmpty();
  mvc.perform(get(base).with(user(a.getEmail()))).andExpect(jsonPath("$.lessons.length()").value(0)).andExpect(jsonPath("$.resumeId").doesNotExist()).andExpect(jsonPath("$.allCompleted").value(false));
  long next=create("Remove with course");mvc.perform(post(base+"/"+next+"/visit").with(user(a.getEmail())).with(csrf())).andExpect(status().isOk());
  mvc.perform(delete("/api/courses/"+course.getId()).with(user(a.getEmail()).roles("ADMIN")).with(csrf())).andExpect(status().isOk());
  assertThat(lessons.findById(next)).isEmpty();assertThat(marks.findByUserIdAndLessonId(a.getId(),next)).isEmpty();
 }
 @Test void courseWithAnotherStudentsAssignmentCannotBeRemovedThroughEitherEntryPoint() throws Exception {
  long lesson=create("Preserved lesson");
  mvc.perform(post(base+"/"+lesson+"/visit").with(user(b.getEmail())).with(csrf())).andExpect(status().isOk());
  var assignment=new Assignment();assignment.setTitle("Keep student work");assignment.setStudent(b);assignment.setCourse(course);
  assignment.setDueDate(java.time.LocalDate.of(2030,1,1));assignments.saveAndFlush(assignment);
  mvc.perform(delete("/api/courses/"+course.getId()).with(user(a.getEmail()).roles("ADMIN")).with(csrf()))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("задания")));
  mvc.perform(post("/courses/"+course.getId()+"/delete").with(user(a.getEmail()).roles("ADMIN")).with(csrf()))
   .andExpect(status().isConflict()).andExpect(view().name("courses"))
   .andExpect(content().string(org.hamcrest.Matchers.containsString("Курс нельзя удалить")));
  assertThat(courses.existsById(course.getId())).isTrue();
  assertThat(assignments.findById(assignment.getId()).orElseThrow().getCourse().getId()).isEqualTo(course.getId());
  assertThat(lessons.findById(lesson)).isPresent();
  assertThat(marks.findByUserIdAndLessonId(b.getId(),lesson)).isPresent();
 }
 @Test void courseWithStudyStepsIsProtectedUntilOwnerRemovesThem() throws Exception {
  String steps="/api/courses/"+course.getId()+"/study-steps";
  var result=mvc.perform(post(steps).with(user(b.getEmail())).with(csrf()).contentType("application/json").content("{\"title\":\"Learn lesson\"}"))
   .andExpect(status().isOk()).andReturn();
  long step=json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  mvc.perform(delete("/api/courses/"+course.getId()).with(user(a.getEmail()).roles("ADMIN")).with(csrf()))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("учебные шаги")));
  assertThat(courses.existsById(course.getId())).isTrue();
  mvc.perform(delete(steps+"/"+step).with(user(b.getEmail())).with(csrf())).andExpect(status().isOk());
  mvc.perform(delete("/api/courses/"+course.getId()).with(user(a.getEmail()).roles("ADMIN")).with(csrf())).andExpect(status().isOk());
 }
 @Test void validationAndCompletionCsrfAreEnforced()throws Exception{
  mvc.perform(post(base).with(user(a.getEmail()).roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"sectionTitle\":\"x\",\"title\":\"x\",\"content\":\"x\",\"sectionOrder\":0,\"lessonOrder\":1}")).andExpect(status().isBadRequest());
  long id=create("Validate");
  mvc.perform(put(base+"/"+id+"/progress").with(user(a.getEmail())).contentType("application/json").content("{\"completed\":true}")).andExpect(status().isForbidden());
  mvc.perform(put(base+"/"+id+"/progress").with(user(a.getEmail())).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
 }
}
