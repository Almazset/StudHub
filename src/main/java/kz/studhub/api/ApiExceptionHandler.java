package kz.studhub.api;

import kz.studhub.chat.ChatService;
import kz.studhub.service.ResourceNotFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ChatService.Failure.class)
    ResponseEntity<Map<String,String>> chatFailure(ChatService.Failure failure) {
        return ResponseEntity.status(failure.status()).cacheControl(CacheControl.noStore())
            .body(Map.of("error", failure.getMessage()));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String,String>> uploadTooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).cacheControl(CacheControl.noStore())
            .body(Map.of("error", "FILE_LIMIT"));
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<Map<String,String>> notFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String,String>> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage()).collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String,String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
