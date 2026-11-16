package kz.studhub.study;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="lesson_progress",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","lesson_id"}))
public class LessonProgress {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(name="user_id",nullable=false) Long userId;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="lesson_id",nullable=false) CourseLesson lesson;
 @Column(nullable=false) boolean completed;
 Instant lastViewedAt;
 protected LessonProgress(){}
}
