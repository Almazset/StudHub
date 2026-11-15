package kz.studhub.project;

import java.time.LocalDate;
import java.util.*;
import kz.studhub.model.AssignmentPriority;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectTaskOverview {
    private final ProjectRepository projects;
    private final ProjectTaskRepository tasks;
    private final UserAccountRepository users;
    public ProjectTaskOverview(ProjectRepository projects, ProjectTaskRepository tasks, UserAccountRepository users) {
        this.projects=projects; this.tasks=tasks; this.users=users;
    }
    public record Row(Long id, String title, Long projectId, String projectTitle, LocalDate dueDate,
                      String status, AssignmentPriority priority, boolean assignedToMe) {
        public boolean completed() { return "DONE".equals(status); }
        public boolean overdue() { return !completed() && dueDate != null && dueDate.isBefore(LocalDate.now()); }
    }
    @Transactional(readOnly=true)
    public List<Row> visible(String email) {
        var viewer=users.findByEmailIgnoreCase(email).orElse(null);
        if (viewer==null) return List.of();
        Map<Long, Project> allowed=new HashMap<>();
        projects.visible(viewer.getId()).forEach(p -> allowed.put(p.id,p));
        if (allowed.isEmpty()) return List.of();
        return tasks.findByProjectIdInOrderByIdDesc(allowed.keySet()).stream().map(t -> new Row(t.id,t.title,
            t.projectId,allowed.get(t.projectId).title,t.dueDate,t.status,
            t.priority==null?AssignmentPriority.MEDIUM:t.priority,Objects.equals(t.assigneeId,viewer.getId()))).toList();
    }
}
