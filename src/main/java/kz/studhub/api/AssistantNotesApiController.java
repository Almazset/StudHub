package kz.studhub.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import kz.studhub.assistant.AssistantException;
import kz.studhub.assistant.AssistantNotesService;
import kz.studhub.assistant.AssistantNotesService.NoteDetail;
import kz.studhub.assistant.AssistantNotesService.NoteInput;
import kz.studhub.assistant.AssistantNotesService.NoteSummary;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assistant/notes")
public class AssistantNotesApiController {
    private final AssistantNotesService notes;

    public AssistantNotesApiController(AssistantNotesService notes) {
        this.notes = notes;
    }

    @GetMapping
    public ResponseEntity<List<NoteSummary>> list(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(notes.list(authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<NoteDetail> create(Authentication authentication, @Valid @RequestBody NoteInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
            .body(notes.create(authentication.getName(), input));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoteDetail> read(Authentication authentication, @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(notes.read(authentication.getName(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable long id) {
        notes.delete(authentication.getName(), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @ExceptionHandler(AssistantException.class)
    public ResponseEntity<Map<String, String>> failure(AssistantException ex) {
        return ResponseEntity.status(ex.status()).cacheControl(CacheControl.noStore())
            .body(Map.of("error", ex.code()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalid() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
            .body(Map.of("error", "VALIDATION"));
    }
}
