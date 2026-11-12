package kz.studhub.study;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
import kz.studhub.repository.CourseRepository;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/courses/{courseId}/study-steps")
public class StudyProgressController {
 private final StudyStepRepository steps; private final CourseRepository courses; private final UserAccountRepository users;
 public StudyProgressController(StudyStepRepository steps,CourseRepository courses,UserAccountRepository users){this.steps=steps;this.courses=courses;this.users=users;}
 public record Create(@NotBlank @Size(max=150) String title) {}
 public record Update(@NotNull Boolean completed) {}
 private Long user(Principal p){return users.findByEmailIgnoreCase(p.getName()).orElseThrow().getId();}
 private void course(Long id){if(!courses.existsById(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
 private StudyStep step(Long id,Long user,Long course){return steps.findByIdAndUserIdAndCourseId(id,user,course).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
 private Map<String,Object> view(StudyStep s){return Map.of("id",s.id,"title",s.title,"completed",s.completed);}
 @GetMapping @Transactional(readOnly=true) public List<Map<String,Object>> list(Principal p,@PathVariable Long courseId){course(courseId);return steps.findByUserIdAndCourseIdOrderByIdAsc(user(p),courseId).stream().map(this::view).toList();}
 @PostMapping @Transactional public Map<String,Object> create(Principal p,@PathVariable Long courseId,@Valid @RequestBody Create input){
  Long id=users.findLockedByEmail(p.getName()).orElseThrow().getId();course(courseId);String title=input.title().strip();
  if(title.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  if(steps.countByUserIdAndCourseId(id,courseId)>=100)throw new ResponseStatusException(HttpStatus.CONFLICT,"Не более 100 этапов на курс");
  return view(steps.saveAndFlush(new StudyStep(id,courseId,title)));
 }
 @PutMapping("/{id}") @Transactional public Map<String,Object> update(Principal p,@PathVariable Long courseId,@PathVariable Long id,@Valid @RequestBody Update input){course(courseId);StudyStep s=step(id,user(p),courseId);s.completed=input.completed();return view(s);}
 @DeleteMapping("/{id}") @Transactional public Map<String,Boolean> delete(Principal p,@PathVariable Long courseId,@PathVariable Long id){course(courseId);steps.delete(step(id,user(p),courseId));return Map.of("deleted",true);}
}
