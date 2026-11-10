package kz.studhub.web;
import kz.studhub.model.*;
import kz.studhub.service.*;
import kz.studhub.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssignmentPageRenderingTest {
 @Autowired MockMvc mvc;
 @MockitoBean AssignmentService assignments;
 @Test void populatedAssignmentListRendersEveryPriority() throws Exception {
  Course course=new Course(); course.setId(1L); course.setTitle("Test course");
  List<Assignment> rows=new ArrayList<>();
  for(AssignmentPriority priority:AssignmentPriority.values()) {
   Assignment a=new Assignment();a.setId((long)rows.size()+1);a.setTitle("Task "+priority);a.setCourse(course);a.setDueDate(LocalDate.now().plusDays(2));a.setPriority(priority);rows.add(a);
  }
  when(assignments.findAll()).thenReturn(rows);
  mvc.perform(get("/assignments").with(user("render@example.test"))).andExpect(status().isOk())
   .andExpect(content().string(containsString("priority-high")))
   .andExpect(content().string(containsString("priority-medium")))
   .andExpect(content().string(containsString("priority-low")));
 }
}
