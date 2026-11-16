package kz.studhub.project;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="study_project_events", indexes=@Index(name="idx_project_event_history", columnList="projectId,id"))
public class ProjectEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false) Long projectId;
    @Column(nullable=false) Long actorId;
    @Column(nullable=false) Instant occurredAt;
    @Column(nullable=false,length=500) String description;
    protected ProjectEvent() {}
}
