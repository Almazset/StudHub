package kz.studhub.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Entity
@Table(name = "assignments")
public class Assignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank @Size(min = 2, max = 150)
    @Column(nullable = false, length = 150)
    private String title;

    @Size(max = 1500)
    @Column(length = 1500)
    private String description;

    @NotNull @Column(nullable = false)
    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private LocalDate dueDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private UserAccount student;

    @Column(nullable = false)
    private boolean completed = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private AssignmentPriority priority = AssignmentPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AssignmentStatus status;

    public AssignmentStatus getStatus() { return status == null ? (completed ? AssignmentStatus.DONE : AssignmentStatus.TODO) : status; }
    public void setStatus(AssignmentStatus status) { this.status = status; this.completed = status == AssignmentStatus.DONE; }

    public Assignment() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public UserAccount getStudent() { return student; }
    public void setStudent(UserAccount student) { this.student = student; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; this.status = completed ? AssignmentStatus.DONE : AssignmentStatus.TODO; }
    public AssignmentPriority getPriority() { return priority == null ? AssignmentPriority.MEDIUM : priority; }
    public void setPriority(AssignmentPriority priority) { this.priority = priority; }
}
