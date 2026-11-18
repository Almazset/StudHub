package kz.studhub.api;

import jakarta.validation.Valid;
import java.util.*;
import kz.studhub.assistant.*;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;

@RestController
@RequestMapping("/api/assistant")
public class AssistantApiController {
    private final GeminiClient gemini;
    private final LectureFileReader files;
    private final AssistantUsageLimiter usage;
    private final UserAccountRepository users;
    public AssistantApiController(GeminiClient gemini, LectureFileReader files, AssistantUsageLimiter usage, UserAccountRepository users) {
        this.gemini = gemini; this.files = files; this.usage = usage; this.users = users;
    }
    @GetMapping("/status") public ResponseEntity<?> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("configured",gemini.configured(),"provider","Google Gemini","maxFileBytes",LectureFileReader.MAX_FILE_BYTES,"maxTotalBytes",LectureFileReader.MAX_TOTAL_BYTES,"maxFiles",3));
    }
    @PostMapping(value="/messages",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssistantAnswer> message(Authentication authentication, @Valid @RequestPart("request") AssistantMessage request,
                                                   @RequestPart(value="files",required=false) List<MultipartFile> uploads) {
        boolean hasFiles = uploads != null && !uploads.isEmpty();
        if (request.message().isBlank() && !hasFiles)
            throw new AssistantException(HttpStatus.BAD_REQUEST, "EMPTY_MESSAGE");
        if (hasFiles && !request.fileConsent())
            throw new AssistantException(HttpStatus.BAD_REQUEST, "FILE_CONSENT_REQUIRED");
        if (!gemini.configured()) throw new AssistantException(HttpStatus.SERVICE_UNAVAILABLE,"NOT_CONFIGURED");
        Long accountId = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow(() -> new AssistantException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED")).getId();
        try (var lease = usage.acquire(accountId)) {
            var lectures = files.readAll(uploads);
            var answer = gemini.answer(request,lectures);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(answer);
        }
    }
    @ExceptionHandler(AssistantException.class) public ResponseEntity<?> failure(AssistantException ex) {
        return ResponseEntity.status(ex.status()).cacheControl(CacheControl.noStore()).body(Map.of("error",ex.code()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<?> invalid() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("error","VALIDATION"));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class) public ResponseEntity<?> tooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).cacheControl(CacheControl.noStore()).body(Map.of("error","FILE_LIMIT"));
    }
}
