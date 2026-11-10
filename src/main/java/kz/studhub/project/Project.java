package kz.studhub.project;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name="study_projects")
public class Project {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false, length=120) String title;
    @Column(nullable=false, length=1000) String description = "";
    @Column(nullable=false) Long ownerId;
    @ElementCollection(fetch=FetchType.LAZY)
    @CollectionTable(name="study_project_members", joinColumns=@JoinColumn(name="project_id"), uniqueConstraints=@UniqueConstraint(columnNames={"project_id","user_id"}))
    @Column(name="user_id", nullable=false) Set<Long> memberIds = new HashSet<>();
    protected Project() {}
}
