package kz.studhub.project;

import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ProjectLifecycleTest {
 @Autowired MockMvc mvc; @Autowired UserAccountRepository users;
 @Autowired ProjectRepository projects; @Autowired ProjectTaskRepository tasks; @Autowired ProjectHistory history;
 UserAccount owner,member,guest;Project project;String base;
 UserAccount account(){var u=new UserAccount();u.setEmail(UUID.randomUUID()+"@lifecycle.invalid");u.setFullName("Lifecycle user");u.setPassword("unused-hash");u.setProfileVisibility("members");return users.saveAndFlush(u);}
 @BeforeEach void setup(){owner=account();member=account();guest=account();project=new Project();project.ownerId=owner.getId();project.title="Lifecycle";project.memberIds.add(owner.getId());projects.saveAndFlush(project);base="/api/projects/"+project.id;}
 void invite()throws Exception{mvc.perform(post(base+"/members").with(user(owner.getEmail())).with(csrf()).contentType("application/json").content("{\"userId\":"+member.getId()+"}")).andExpect(status().isOk()).andExpect(jsonPath("$.members.length()").value(1));}
 void accept()throws Exception{mvc.perform(post(base+"/invitation/accept").with(user(member.getEmail())).with(csrf())).andExpect(status().isOk());}
 void close()throws Exception{mvc.perform(patch(base+"/state").with(user(owner.getEmail())).with(csrf()).contentType("application/json").content("{\"closed\":true}")).andExpect(status().isOk());}
 @Test void invitationDoesNotGrantAccessUntilRecipientAccepts()throws Exception{
  invite();invite();assertThat(project.invitedIds).hasSize(1);
  for(String suffix:List.of("","/tasks","/history"))mvc.perform(get(base+suffix).with(user(member.getEmail()))).andExpect(status().isNotFound());
  mvc.perform(post(base+"/chat").with(user(member.getEmail())).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(get("/api/projects/invitations").with(user(member.getEmail()))).andExpect(jsonPath("$[0].id").value(project.id));
  mvc.perform(get("/api/projects/invitations").with(user(guest.getEmail()))).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(post(base+"/invitation/accept").with(user(guest.getEmail())).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(post(base+"/invitation/accept").with(user(member.getEmail()))).andExpect(status().isForbidden());
  accept();assertThat(project.memberIds).contains(member.getId());assertThat(project.invitedIds).isEmpty();
  mvc.perform(post(base+"/invitation/accept").with(user(member.getEmail())).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(get(base+"/history").with(user(member.getEmail()))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
 }
 @Test void closingPreservesDataBlocksWritesAndOwnerCanReopen()throws Exception{
  invite();accept();var task=new ProjectTask();task.projectId=project.id;task.title="Keep me";task.assigneeId=member.getId();tasks.saveAndFlush(task);
  for(var u:List.of(member,guest))mvc.perform(patch(base+"/state").with(user(u.getEmail())).with(csrf()).contentType("application/json").content("{\"closed\":true}")).andExpect(status().isNotFound());
  close();close();assertThat(history.list(project.id,null)).hasSize(3);
  mvc.perform(get(base).with(user(member.getEmail()))).andExpect(status().isOk()).andExpect(jsonPath("$.closed").value(true)).andExpect(jsonPath("$.members.length()").value(2));
  mvc.perform(get(base+"/tasks").with(user(member.getEmail()))).andExpect(jsonPath("$[0].title").value("Keep me"));
  mvc.perform(get("/projects/"+project.id).with(user(owner.getEmail()))).andExpect(status().isOk());
  mvc.perform(put(base).with(user(owner.getEmail())).with(csrf()).contentType("application/json").content("{\"title\":\"No\"}")).andExpect(status().isConflict());
  mvc.perform(delete(base+"/members/"+member.getId()).with(user(owner.getEmail())).with(csrf())).andExpect(status().isConflict());
  mvc.perform(post(base+"/tasks").with(user(owner.getEmail())).with(csrf()).contentType("application/json").content("{\"title\":\"No\"}")).andExpect(status().isConflict());
  mvc.perform(put(base+"/tasks/"+task.id).with(user(member.getEmail())).with(csrf()).contentType("application/json").content("{\"title\":\"No\"}")).andExpect(status().isConflict());
  mvc.perform(patch(base+"/tasks/"+task.id).with(user(member.getEmail())).with(csrf()).contentType("application/json").content("{\"status\":\"DONE\"}")).andExpect(status().isConflict());
  mvc.perform(delete(base+"/tasks/"+task.id).with(user(owner.getEmail())).with(csrf())).andExpect(status().isConflict());
  assertThat(tasks.findById(task.id)).isPresent();assertThat(project.memberIds).hasSize(2);
  mvc.perform(patch(base+"/state").with(user(owner.getEmail())).with(csrf()).contentType("application/json").content("{\"closed\":false}")).andExpect(status().isOk());
  mvc.perform(patch(base+"/tasks/"+task.id).with(user(member.getEmail())).with(csrf()).contentType("application/json").content("{\"status\":\"DONE\"}")).andExpect(status().isOk());
  assertThat(history.list(project.id,null)).hasSize(5);
 }
 @Test void declinedAndCancelledInvitationsDoNotBecomeMembership()throws Exception{
  invite();close();mvc.perform(post(base+"/invitation/accept").with(user(member.getEmail())).with(csrf())).andExpect(status().isConflict());
  mvc.perform(post(base+"/invitation/decline").with(user(member.getEmail())).with(csrf())).andExpect(status().isOk());
  project.closed=false;projects.saveAndFlush(project);invite();
  mvc.perform(delete(base+"/invitations/"+member.getId()).with(user(guest.getEmail())).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(delete(base+"/invitations/"+member.getId()).with(user(owner.getEmail())).with(csrf())).andExpect(status().isOk());
  mvc.perform(post(base+"/invitation/accept").with(user(member.getEmail())).with(csrf())).andExpect(status().isNotFound());
  assertThat(project.memberIds).containsExactly(owner.getId());
 }
 @Test void historyIsPagedPrivateAndRetainsDeletedTaskDescription()throws Exception{
  for(int i=0;i<55;i++){var event=new ProjectEvent();event.projectId=project.id;event.actorId=owner.getId();event.occurredAt=java.time.Instant.now();event.description="Event "+i;events.save(event);}events.flush();
  var first=history.list(project.id,null);assertThat(first).hasSize(50);assertThat(history.list(project.id,first.get(49).id())).hasSize(5);
  mvc.perform(get(base+"/history").with(user(guest.getEmail()).roles("ADMIN"))).andExpect(status().isNotFound());
  var task=new ProjectTask();task.projectId=project.id;task.title="Remember deleted task";tasks.saveAndFlush(task);
  mvc.perform(delete(base+"/tasks/"+task.id).with(user(owner.getEmail())).with(csrf())).andExpect(status().isOk());
  assertThat(history.list(project.id,null).get(0).description()).contains("Remember deleted task");
 }
 @Autowired ProjectEventRepository events;
}
