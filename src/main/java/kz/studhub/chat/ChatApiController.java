package kz.studhub.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import org.springframework.web.multipart.MultipartFile;
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
    public record Send(@NotBlank @Size(max=4000) String text, @NotNull UUID clientId, @Positive Long replyToId) {}
    public record Read(@Positive long messageId) {}
    public record Edit(@NotNull @Size(max=4000) String text) {}
    public record Reaction(@NotNull @Size(max=16) String emoji) {}
    public record Flag(@NotNull Boolean enabled) {}
    public record Forward(@NotBlank @Size(max=36) String roomId,@NotNull UUID clientId) {}
    public record Refresh(@NotNull @Size(min=1,max=500) Set<@NotNull @Positive Long> ids) {}
    @PutMapping("/rooms/{room}/messages/{id}/reaction") public ResponseEntity<?> reaction(Principal u,@PathVariable String room,@PathVariable Long id,@Valid @RequestBody Reaction r) {return ok(chat.reaction(u.getName(),room,id,r.emoji()));}
    @PutMapping("/rooms/{room}/messages/{id}/favorite") public ResponseEntity<?> favorite(Principal u,@PathVariable String room,@PathVariable Long id,@Valid @RequestBody Flag r) {return ok(chat.favorite(u.getName(),room,id,r.enabled()));}
    @PutMapping("/rooms/{room}/messages/{id}/pin") public ResponseEntity<?> pin(Principal u,@PathVariable String room,@PathVariable Long id,@Valid @RequestBody Flag r) {return ok(chat.pin(u.getName(),room,id,r.enabled()));}
    @PostMapping("/rooms/{room}/messages/{id}/forward") public ResponseEntity<?> forward(Principal u,@PathVariable String room,@PathVariable Long id,@Valid @RequestBody Forward r) {return ok(chat.forward(u.getName(),room,id,r.roomId(),r.clientId()));}
    @GetMapping("/rooms/{room}/saved") public ResponseEntity<?> saved(Principal u,@PathVariable String room,@RequestParam String kind) {return ok(chat.saved(u.getName(),room,kind));}
    @PostMapping("/rooms/{room}/refresh") public ResponseEntity<?> refresh(Principal u,@PathVariable String room,@Valid @RequestBody Refresh r) {return ok(chat.refresh(u.getName(),room,r.ids()));}
    @GetMapping("/rooms/{room}/around/{id}") public ResponseEntity<?> around(Principal u,@PathVariable String room,@PathVariable Long id) {return ok(chat.around(u.getName(),room,id));}
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
    @PostMapping(path="/rooms/{id}/messages", consumes=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<?> send(Principal user, @PathVariable String id, @Valid @RequestBody Send request) { return ok(chat.send(user.getName(),id,request.text(),request.clientId(),request.replyToId())); }
    public record Upload(@NotNull @Size(max=4000) String text, @NotNull UUID clientId, @Positive Long replyToId) {}

    @PostMapping(path = "/rooms/{room}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(Principal user, @PathVariable String room, @Valid @RequestPart("message") Upload message,
                                    @RequestPart("file") MultipartFile file) {
        return ok(chat.send(user.getName(), room, message.text(), message.clientId(), message.replyToId(), file));
    }

    @GetMapping("/rooms/{room}/messages/{id}/attachment")
    public ResponseEntity<byte[]> download(Principal user, @PathVariable String room, @PathVariable @Positive Long id) {
        var file = chat.download(user.getName(), room, id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(file.data().length)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.name(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(file.data());
    }
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
