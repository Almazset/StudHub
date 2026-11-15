package kz.studhub.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import kz.studhub.model.*;
import kz.studhub.repository.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AssignmentWorkspaceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserAccountRepository users;
    @Autowired AssignmentRepository assignments;
    @Autowired CourseRepository courses;
    @Autowired ProjectRepository projects;
    @Autowired ProjectTaskRepository tasks;
    @Autowired ProjectTaskOverview overview;
    UserAccount alice,bob,outsider;
    Project project; Course course;
    UserAccount account(String name) {var u=new UserAccount();u.setFullName(name);u.setEmail(UUID.randomUUID()+"@workspace.invalid");u.setPassword("unused-test-hash");return users.saveAndFlush(u);}
    @BeforeEach void setup() {
        alice=account("Alice");bob=account("Bob");outsider=account("Outsider");
        course=new Course();course.setTitle("Course workspace");course.setTeacherName("Teacher");courses.saveAndFlush(course);
        project=new Project();project.title="Team workspace";project.ownerId=alice.getId();project.memberIds.addAll(Set.of(alice.getId(),bob.getId()));projects.saveAndFlush(project);
    }
    Assignment personal(UserAccount owner,String title,Course relation) {
        var a=new Assignment();a.setTitle(title);a.setStudent(owner);a.setCourse(relation);a.setDueDate(LocalDate.now().minusDays(1));return assignments.saveAndFlush(a);
    }
    ProjectTask teamTask() {var t=new ProjectTask();t.projectId=project.id;t.title="Assigned team task";t.assigneeId=bob.getId();t.priority=AssignmentPriority.HIGH;return tasks.saveAndFlush(t);}
    @Test void personalTasksWithoutCourseRenderAcrossDashboardAlertsAndCoursePage() throws Exception {
        personal(alice,"PERSONAL_VISIBLE",null);personal(bob,"BOB_HIDDEN",null);
        for(String path:List.of("/","/assignments","/api/assignments")) {
            mvc.perform(get(path).with(user(alice.getEmail()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("PERSONAL_VISIBLE"))).andExpect(content().string(not(containsString("BOB_HIDDEN"))));
        }
        mvc.perform(get("/courses/"+course.getId()).with(user(alice.getEmail()))).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("assignment-title\">PERSONAL_VISIBLE"))));
        mvc.perform(get("/assignments/new").with(user(alice.getEmail()))).andExpect(status().isOk())
            .andExpect(content().string(containsString("Личное задание — без курса")));
    }
    @Test void statusAndOptionalCoursePersistWithoutAcceptingAnotherOwner() throws Exception {
        var payload=new LinkedHashMap<String,Object>();payload.put("title","New personal task");payload.put("dueDate","2027-01-01");payload.put("status","IN_PROGRESS");payload.put("priority","HIGH");payload.put("student",Map.of("id",bob.getId()));
        var created=mvc.perform(post("/api/assignments").with(user(alice.getEmail())).with(csrf()).contentType("application/json").content(json.writeValueAsString(payload)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.course").doesNotExist()).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.completed").value(false)).andReturn();
        long id=json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        assertThat(assignments.findById(id).orElseThrow().getStudent().getId()).isEqualTo(alice.getId());
        payload.put("courseId",course.getId());payload.put("status","DONE");
        mvc.perform(put("/api/assignments/"+id).with(user(alice.getEmail())).with(csrf()).contentType("application/json").content(json.writeValueAsString(payload)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(true)).andExpect(jsonPath("$.course.id").value(course.getId()));
        mvc.perform(post("/assignments/"+id+"/toggle-completed").with(user(alice.getEmail())).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(assignments.findById(id).orElseThrow().getStatus()).isEqualTo(AssignmentStatus.TODO);
        payload.put("status","FORGED");
        mvc.perform(put("/api/assignments/"+id).with(user(alice.getEmail())).with(csrf()).contentType("application/json").content(json.writeValueAsString(payload))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/assignments/"+id).with(user(bob.getEmail()))).andExpect(status().isNotFound());
    }
    @Test void overviewOnlyIncludesVisibleProjectsAndMarksTheActualAssignee() throws Exception {
        var t=teamTask(); personal(alice,"ALICE_PRIVATE",null);personal(bob,"BOB_PRIVATE",course);
        assertThat(overview.visible(alice.getEmail())).singleElement().satisfies(row->{assertThat(row.assignedToMe()).isFalse();assertThat(row.overdue()).isFalse();});
        assertThat(overview.visible(bob.getEmail())).singleElement().satisfies(row->{assertThat(row.assignedToMe()).isTrue();assertThat(row.priority()).isEqualTo(AssignmentPriority.HIGH);});
        assertThat(overview.visible(outsider.getEmail())).isEmpty();
        mvc.perform(get("/assignments").with(user(bob.getEmail()))).andExpect(status().isOk())
            .andExpect(content().string(containsString("Assigned team task"))).andExpect(content().string(containsString("#task-"+t.id)))
            .andExpect(content().string(containsString("data-assigned=\"true\""))).andExpect(content().string(not(containsString("ALICE_PRIVATE"))));
        mvc.perform(get("/assignments").with(user(outsider.getEmail()).roles("ADMIN"))).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("Assigned team task"))));
        project.memberIds.remove(bob.getId());projects.saveAndFlush(project);
        assertThat(overview.visible(bob.getEmail())).isEmpty();
    }
    @Test void projectPriorityIsValidatedAndOwnerOnlyDeletionStillApplies() throws Exception {
        String base="/api/projects/"+project.id+"/tasks";
        var created=mvc.perform(post(base).with(user(bob.getEmail())).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("title","Priority task","priority","HIGH","assigneeId",bob.getId(),"dueDate","2027-01-01"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.priority").value("HIGH")).andReturn();
        long id=json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(put(base+"/"+id).with(user(bob.getEmail())).with(csrf()).contentType("application/json")
            .content("{\"title\":\"Changed priority\",\"priority\":\"LOW\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.priority").value("LOW"));
        mvc.perform(post(base).with(user(bob.getEmail())).with(csrf()).contentType("application/json").content("{\"title\":\"Invalid task\",\"priority\":\"FORGED\"}")).andExpect(status().isBadRequest());
        mvc.perform(delete(base+"/"+id).with(user(bob.getEmail())).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete(base+"/"+id).with(user(alice.getEmail())).with(csrf())).andExpect(status().isOk());
    }
}
