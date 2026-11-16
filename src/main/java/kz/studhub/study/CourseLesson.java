package kz.studhub.study;
import jakarta.persistence.*;
@Entity @Table(name="course_lessons",indexes=@Index(name="course_lesson_order",columnList="courseId,sectionOrder,lessonOrder"))
public class CourseLesson {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(nullable=false) Long courseId;
 @Column(nullable=false,length=120) String sectionTitle;
 @Column(nullable=false) int sectionOrder;
 @Column(nullable=false) int lessonOrder;
 @Column(nullable=false,length=150) String title;
 @Column(nullable=false,length=20000) String content;
 protected CourseLesson(){}
}
