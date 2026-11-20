package kz.studhub.study;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import kz.studhub.repository.CourseRepository;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/courses/{courseId}/lessons")
public class CourseLearningController {
 private final CourseLessonRepository lessons;private final LessonProgressRepository progress;
 private final CourseRepository courses;private final UserAccountRepository users;
 public CourseLearningController(CourseLessonRepository l,LessonProgressRepository p,CourseRepository c,UserAccountRepository u){lessons=l;progress=p;courses=c;users=u;}
 public record Input(@NotBlank @Size(max=120) String sectionTitle,@Min(1) @Max(1000) int sectionOrder,@Min(1) @Max(1000) int lessonOrder,@NotBlank @Size(max=150) String title,@NotBlank @Size(max=20000) String content){}
 public record Completion(@NotNull Boolean completed){}
 public record Lesson(Long id,String sectionTitle,int sectionOrder,int lessonOrder,String title,boolean completed){}
 public record Outline(List<Lesson> lessons,Long resumeId,int completedCount,boolean allCompleted){}
 private Long user(Principal p){return users.findByEmailIgnoreCase(p.getName()).orElseThrow().getId();}
 private void internal(Long id){var c=courses.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if(c.isExternal())throw new ResponseStatusException(HttpStatus.CONFLICT,"Этот курс проходит на внешней платформе");}
 private CourseLesson lesson(Long course,Long id){return lessons.findByIdAndCourseId(id,course).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
 private void lock(Long id){courses.findLockedById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));internal(id);}
 @GetMapping @Transactional(readOnly=true)
 public Outline outline(Principal p,@PathVariable Long courseId){
  internal(courseId);var rows=lessons.findByCourseIdOrderBySectionOrderAscLessonOrderAscIdAsc(courseId);var marks=progress.findByUserIdAndLessonCourseId(user(p),courseId);
  var done=new HashSet<Long>();marks.stream().filter(m->m.completed).forEach(m->done.add(m.lesson.id));
  var next=marks.stream().filter(m->!m.completed&&m.lastViewedAt!=null).max(Comparator.comparing(m->m.lastViewedAt)).map(m->m.lesson.id).orElse(null);
  if(next==null)next=rows.stream().filter(l->!done.contains(l.id)).map(l->l.id).findFirst().orElse(rows.isEmpty()?null:rows.get(0).id);
  return new Outline(rows.stream().map(l->new Lesson(l.id,l.sectionTitle,l.sectionOrder,l.lessonOrder,l.title,done.contains(l.id))).toList(),next,done.size(),!rows.isEmpty()&&done.size()==rows.size());
 }
 @GetMapping("/{id}") @Transactional(readOnly=true)
 public Map<String,Object> read(@PathVariable Long courseId,@PathVariable Long id){internal(courseId);var l=lesson(courseId,id);return Map.of("id",l.id,"sectionTitle",l.sectionTitle,"sectionOrder",l.sectionOrder,"lessonOrder",l.lessonOrder,"title",l.title,"content",l.content);}
 private LessonProgress mark(Principal p,CourseLesson l){Long uid=users.findLockedByEmail(p.getName()).orElseThrow().getId();return progress.findByUserIdAndLessonId(uid,l.id).orElseGet(()->{var m=new LessonProgress();m.userId=uid;m.lesson=l;return m;});}
 @PostMapping("/{id}/visit") @Transactional
 public Map<String,Boolean> visit(Principal p,@PathVariable Long courseId,@PathVariable Long id){lock(courseId);var m=mark(p,lesson(courseId,id));m.lastViewedAt=Instant.now();progress.save(m);return Map.of("saved",true);}
 @PutMapping("/{id}/progress") @Transactional
 public Map<String,Boolean> complete(Principal p,@PathVariable Long courseId,@PathVariable Long id,@Valid @RequestBody Completion input){lock(courseId);var m=mark(p,lesson(courseId,id));m.completed=input.completed();progress.save(m);return Map.of("completed",m.completed);}
 private void fill(CourseLesson l,Input input){l.sectionTitle=input.sectionTitle().strip();l.sectionOrder=input.sectionOrder();l.lessonOrder=input.lessonOrder();l.title=input.title().strip();l.content=input.content().strip();}
 @PostMapping @PreAuthorize("hasRole('ADMIN')") @Transactional
 public Map<String,Long> create(@PathVariable Long courseId,@Valid @RequestBody Input input){lock(courseId);if(lessons.countByCourseId(courseId)>=100)throw new ResponseStatusException(HttpStatus.CONFLICT,"Не более 100 уроков");var l=new CourseLesson();l.courseId=courseId;fill(l,input);lessons.saveAndFlush(l);return Map.of("id",l.id);}
 @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional
 public Map<String,Long> edit(@PathVariable Long courseId,@PathVariable Long id,@Valid @RequestBody Input input){lock(courseId);var l=lesson(courseId,id);fill(l,input);return Map.of("id",id);}
 @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional
 public Map<String,Boolean> delete(@PathVariable Long courseId,@PathVariable Long id){lock(courseId);var l=lesson(courseId,id);progress.deleteByLessonId(id);progress.flush();lessons.delete(l);return Map.of("deleted",true);}
}
