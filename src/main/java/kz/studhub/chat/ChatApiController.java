package kz.studhub.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestController
@RequestMapping("/api/chat")
public class ChatApiController {
    public record Direct(@NotNull @Positive Long userId) {}
    public record Group(@NotBlank @Size(min=2,max=80) String name, @NotNull @Size(min=1,max=29) Set<@NotNull @Positive Long> userIds) {}
    public record Members(@NotNull @Size(min=1,max=29) Set<@NotNull @Positive Long> userIds) {}
    public record Send(@NotBlank @Size(max=4000) String text, @NotNull UUID clientId) {}
    public record Read(@Positive long messageId) {}
    public record Edit(@NotBlank @Size(max=4000) String text) {}
    private final ChatService chat;
    public ChatApiController(ChatService chat) { this.chat = chat; }
    private <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
    @GetMapping("/rooms") public ResponseEntity<?> list(Principal user) { return ok(chat.list(user.getName())); }
    @GetMapping("/users") public ResponseEntity<?> users(Principal user, @RequestParam(defaultValue="") @Size(max=80) String q) { return ok(chat.search(user.getName(),q)); }
    @PostMapping("/direct") public ResponseEntity<?> direct(Principal user, @Valid @RequestBody Direct request) { return ok(chat.direct(user.getName(),request.userId())); }
    @PostMapping("/groups") public ResponseEntity<?> group(Principal user, @Valid @RequestBody Group request) { return ok(chat.group(user.getName(),request.name(),request.userIds())); }
    @GetMapping("/rooms/{id}") public ResponseEntity<?> detail(Principal user, @PathVariable String id) { return ok(chat.detail(user.getName(),id)); }
    @GetMapping("/rooms/{id}/messages") public ResponseEntity<?> history(Principal user, @PathVariable String id,
        @RequestParam(required=false) @Positive Long before, @RequestParam(required=false) @PositiveOrZero Long after) { return ok(chat.history(user.getName(),id,before,after)); }
    @PostMapping("/rooms/{id}/messages") public ResponseEntity<?> send(Principal user, @PathVariable String id, @Valid @RequestBody Send request) { return ok(chat.send(user.getName(),id,request.text(),request.clientId())); }
    @PutMapping("/rooms/{id}/messages/{messageId}") public ResponseEntity<?> edit(Principal user,@PathVariable String id,@PathVariable @Positive Long messageId,@Valid @RequestBody Edit request) { return ok(chat.editMessage(user.getName(),id,messageId,request.text())); }
    @DeleteMapping("/rooms/{id}/messages/{messageId}") public ResponseEntity<?> delete(Principal user,@PathVariable String id,@PathVariable @Positive Long messageId) { chat.deleteMessage(user.getName(),id,messageId); return ok(Map.of("deleted",true)); }
    @GetMapping("/rooms/{id}/search") public ResponseEntity<?> searchMessages(Principal user,@PathVariable String id,@RequestParam @Size(min=2,max=120) String q) { return ok(chat.searchMessages(user.getName(),id,q)); }
    @PostMapping("/rooms/{id}/read") public ResponseEntity<?> read(Principal user, @PathVariable String id, @Valid @RequestBody Read request) { chat.read(user.getName(),id,request.messageId()); return ok(Map.of("saved",true)); }
    @PostMapping("/rooms/{id}/members") public ResponseEntity<?> add(Principal user, @PathVariable String id, @Valid @RequestBody Members request) { chat.addMembers(user.getName(),id,request.userIds()); return ok(Map.of("saved",true)); }
    @PostMapping("/rooms/{id}/leave") public ResponseEntity<?> leave(Principal user, @PathVariable String id) { chat.leave(user.getName(),id); return ok(Map.of("saved",true)); }
    @ExceptionHandler(ChatService.Failure.class) public ResponseEntity<?> failure(ChatService.Failure error) {
        return ResponseEntity.status(error.status).cacheControl(CacheControl.noStore()).body(Map.of("error",error.code));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class,HandlerMethodValidationException.class})
    public ResponseEntity<?> invalid(Exception error) { return ResponseEntity.badRequest().body(Map.of("error","VALIDATION")); }
}
