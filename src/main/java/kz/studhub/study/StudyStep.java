package kz.studhub.study;
import jakarta.persistence.*;
@Entity @Table(name="study_steps", indexes=@Index(name="study_step_owner_course",columnList="userId,courseId"))
public class StudyStep {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(nullable=false) Long userId;
 @Column(nullable=false) Long courseId;
 @Column(nullable=false,length=150) String title;
 @Column(nullable=false) boolean completed;
 protected StudyStep() {}
 StudyStep(Long userId,Long courseId,String title){this.userId=userId;this.courseId=courseId;this.title=title;}
}
