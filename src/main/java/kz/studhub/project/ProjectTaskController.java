package kz.studhub.project;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/projects/{projectId}/tasks")
public class ProjectTaskController {
 private final ProjectRepository projects; private final ProjectTaskRepository tasks; private final UserAccountRepository users;
 public ProjectTaskController(ProjectRepository p,ProjectTaskRepository t,UserAccountRepository u){projects=p;tasks=t;users=u;}
 public record Create(@NotBlank @Size(max=150) String title, @Positive Long assigneeId, LocalDate dueDate) {}
 public record Change(@NotBlank @Pattern(regexp="TODO|IN_PROGRESS|REVIEW|DONE") String status) {}
 private Project access(Long id,Authentication auth,boolean lock){
  var viewer=users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
  var project=(lock?projects.locked(id):projects.findById(id)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  if(!project.ownerId.equals(viewer.getId())&&!project.memberIds.contains(viewer.getId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  return project;
 }
 private Map<String,Object> view(ProjectTask t){
  Map<String,Object> v=new LinkedHashMap<>();v.put("id",t.id);v.put("title",t.title);v.put("assigneeId",t.assigneeId);v.put("dueDate",t.dueDate);v.put("status",t.status);return v;
 }
 @GetMapping @Transactional(readOnly=true) public List<Map<String,Object>> list(@PathVariable Long projectId,Authentication auth){access(projectId,auth,false);return tasks.findByProjectIdOrderByIdDesc(projectId).stream().map(this::view).toList();}
 @PostMapping @Transactional public Map<String,Object> create(@PathVariable Long projectId,Authentication auth,@Valid @RequestBody Create input){
  var p=access(projectId,auth,true);if(input.assigneeId()!=null&&!p.memberIds.contains(input.assigneeId()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Исполнитель должен быть участником проекта");
  var title=input.title().strip();if(title.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  var t=new ProjectTask();t.projectId=projectId;t.title=title;t.assigneeId=input.assigneeId();t.dueDate=input.dueDate();return view(tasks.saveAndFlush(t));
 }
 @PutMapping("/{taskId}") @Transactional public Map<String,Object> edit(@PathVariable Long projectId,@PathVariable Long taskId,Authentication auth,@Valid @RequestBody Create input){
  var p=access(projectId,auth,true);
  if(input.assigneeId()!=null&&!p.memberIds.contains(input.assigneeId()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Исполнитель должен быть участником проекта");
  String title=input.title().strip();if(title.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  var t=tasks.findByIdAndProjectId(taskId,projectId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  t.title=title;t.assigneeId=input.assigneeId();t.dueDate=input.dueDate();return view(t);
 }
 @DeleteMapping("/{taskId}") @Transactional public Map<String,Boolean> delete(@PathVariable Long projectId,@PathVariable Long taskId,Authentication auth){
  var p=access(projectId,auth,true);
  if(!p.ownerId.equals(users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  var t=tasks.findByIdAndProjectId(taskId,projectId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  tasks.delete(t);return Map.of("deleted",true);
 }
 @PatchMapping("/{taskId}") @Transactional public Map<String,Object> update(@PathVariable Long projectId,@PathVariable Long taskId,Authentication auth,@Valid @RequestBody Change input){
  access(projectId,auth,true);var t=tasks.findByIdAndProjectId(taskId,projectId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));t.status=input.status();return view(tasks.saveAndFlush(t));
 }
}
