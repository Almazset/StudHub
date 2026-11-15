package kz.studhub.project;
import jakarta.persistence.*;
import java.time.LocalDate;
@Entity @Table(name="project_tasks", indexes=@Index(name="project_task_project",columnList="projectId"))
public class ProjectTask {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(nullable=false) Long projectId;
 @Column(nullable=false,length=150) String title;
 Long assigneeId;
 LocalDate dueDate;
 @Column(nullable=false,length=20) String status="TODO";
 @Enumerated(EnumType.STRING) @Column(length=10) kz.studhub.model.AssignmentPriority priority;
 protected ProjectTask() {}
}
