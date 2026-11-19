package kz.studhub.project;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import kz.studhub.notification.NotificationService;
import kz.studhub.notification.NotificationType;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/projects/{projectId}/tasks")
public class ProjectTaskController {
 private final ProjectHistory history;
 private final ProjectRepository projects; private final ProjectTaskRepository tasks; private final UserAccountRepository users;
 private final NotificationService notifications;
 public ProjectTaskController(ProjectRepository projects, ProjectTaskRepository tasks, UserAccountRepository users,
         ProjectHistory history, NotificationService notifications) {
  this.projects = projects;
  this.tasks = tasks;
  this.users = users;
  this.history = history;
  this.notifications = notifications;
 }
 public record Create(@NotBlank @Size(max=150) String title, @Positive Long assigneeId, LocalDate dueDate, kz.studhub.model.AssignmentPriority priority) {}
 public record Change(@NotBlank @Pattern(regexp="TODO|IN_PROGRESS|REVIEW|DONE") String status) {}
 private Project access(Long id,Authentication auth,boolean lock){
  var viewer=users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
  var project=(lock?projects.locked(id):projects.findById(id)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  if(!project.ownerId.equals(viewer.getId())&&!project.memberIds.contains(viewer.getId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  if(lock)project.requireOpen();
  return project;
 }
 private String statusLabel(String status){return Map.of("TODO","Запланировано","IN_PROGRESS","В работе","REVIEW","На проверке","DONE","Готово").getOrDefault(status,status);}
 private Map<String,Object> view(ProjectTask t){
  Map<String,Object> v=new LinkedHashMap<>();v.put("id",t.id);v.put("title",t.title);v.put("assigneeId",t.assigneeId);v.put("dueDate",t.dueDate);v.put("status",t.status);v.put("priority",t.priority==null?kz.studhub.model.AssignmentPriority.MEDIUM:t.priority);return v;
 }
 @GetMapping @Transactional(readOnly=true) public List<Map<String,Object>> list(@PathVariable Long projectId,Authentication auth){access(projectId,auth,false);return tasks.findByProjectIdOrderByIdDesc(projectId).stream().map(this::view).toList();}
 @PostMapping
 @Transactional
 public Map<String,Object> create(@PathVariable Long projectId, Authentication auth, @Valid @RequestBody Create input) {
  var project = access(projectId, auth, true);
  validateAssignee(project, input.assigneeId());
  var task = new ProjectTask();
  task.projectId = projectId;
  apply(task, input);
  tasks.saveAndFlush(task);
  history.record(projectId, auth, "Задача #" + task.id + ": " + task.title + " · статус " + statusLabel(task.status));
  notifyAssignee(task, null);
  return view(task);
 }

 @PutMapping("/{taskId}")
 @Transactional
 public Map<String,Object> edit(@PathVariable Long projectId, @PathVariable Long taskId, Authentication auth,
         @Valid @RequestBody Create input) {
  var project = access(projectId, auth, true);
  validateAssignee(project, input.assigneeId());
  var task = tasks.findByIdAndProjectId(taskId, projectId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  Long previousAssignee = task.assigneeId;
  apply(task, input);
  history.record(projectId, auth, "Изменена задача #" + task.id + ": " + task.title + " · исполнитель "
      + (task.assigneeId == null ? "не назначен" : "SH-" + task.assigneeId) + " · срок " + task.dueDate
      + " · приоритет " + task.priority);
  notifyAssignee(task, previousAssignee);
  return view(task);
 }

 private void validateAssignee(Project project, Long assignee) {
  if (assignee != null && !project.memberIds.contains(assignee))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Исполнитель должен быть участником проекта");
 }

 private void apply(ProjectTask task, Create input) {
  String title = input.title().strip();
  if (title.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  task.title = title;
  task.assigneeId = input.assigneeId();
  task.dueDate = input.dueDate();
  task.priority = input.priority() == null ? kz.studhub.model.AssignmentPriority.MEDIUM : input.priority();
 }

 private void notifyAssignee(ProjectTask task, Long previousAssignee) {
  if (task.assigneeId == null || Objects.equals(task.assigneeId, previousAssignee) || "DONE".equals(task.status)) return;
  notifications.publish(task.assigneeId, NotificationType.TASK_ASSIGNED,
      "task:" + task.id + ":assignment:" + UUID.randomUUID(), "", "/projects/" + task.projectId);
 }

 @DeleteMapping("/{taskId}") @Transactional public Map<String,Boolean> delete(@PathVariable Long projectId,@PathVariable Long taskId,Authentication auth){
  var p=access(projectId,auth,true);
  if(!p.ownerId.equals(users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  var t=tasks.findByIdAndProjectId(taskId,projectId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  history.record(projectId,auth,"Удалена задача #"+t.id+": "+t.title);tasks.delete(t);return Map.of("deleted",true);
 }
 @PatchMapping("/{taskId}") @Transactional public Map<String,Object> update(@PathVariable Long projectId,@PathVariable Long taskId,Authentication auth,@Valid @RequestBody Change input){
  access(projectId,auth,true);var t=tasks.findByIdAndProjectId(taskId,projectId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));t.status=input.status();tasks.saveAndFlush(t);history.record(projectId,auth,"Задача #"+t.id+": "+t.title+" · статус "+statusLabel(t.status));return view(t);
 }
}
