package kz.studhub.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import kz.studhub.calendar.StudyPlanService;
import kz.studhub.calendar.StudyPlanService.Entry;
import kz.studhub.calendar.StudyPlanService.Input;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/calendar/plan")
public class StudyPlanApiController {
    private final StudyPlanService plans;

    public StudyPlanApiController(StudyPlanService plans) {
        this.plans = plans;
    }

    @GetMapping
    public ResponseEntity<List<Entry>> list(Authentication authentication,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return noStore(HttpStatus.OK, plans.list(authentication.getName(), from, to));
    }

    @PostMapping
    public ResponseEntity<Entry> create(Authentication authentication, @Valid @RequestBody Input input) {
        return noStore(HttpStatus.CREATED, plans.create(authentication.getName(), input));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Entry> update(Authentication authentication,
            @PathVariable @Positive long id, @Valid @RequestBody Input input) {
        return noStore(HttpStatus.OK, plans.update(authentication.getName(), id, input));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable @Positive long id) {
        plans.delete(authentication.getName(), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @ExceptionHandler(StudyPlanService.Failure.class)
    public ResponseEntity<Map<String, String>> failure(StudyPlanService.Failure failure) {
        return noStore(failure.status(), Map.of("error", failure.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> invalid() {
        return noStore(HttpStatus.BAD_REQUEST, Map.of("error", "VALIDATION"));
    }

    private static <T> ResponseEntity<T> noStore(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
