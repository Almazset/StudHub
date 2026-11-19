package kz.studhub.notification;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Positive;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/notifications")
public class NotificationApiController {
    private final NotificationService notifications;
    private final NotificationReminders reminders;

    public NotificationApiController(NotificationService notifications, NotificationReminders reminders) {
        this.notifications = notifications;
        this.reminders = reminders;
    }

    @GetMapping
    public ResponseEntity<NotificationService.Feed> feed(Authentication auth, @RequestParam(required = false) String zone) {
        long recipient = notifications.recipientId(auth.getName());
        reminders.refresh(recipient, zone);
        return noStore(notifications.feed(recipient));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(Authentication auth, @PathVariable @Positive long id) {
        notifications.markRead(notifications.recipientId(auth.getName()), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Authentication auth) {
        notifications.markAllRead(notifications.recipientId(auth.getName()));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/preferences")
    public ResponseEntity<Map<NotificationType, Boolean>> preferences(Authentication auth) {
        return noStore(notifications.preferences(notifications.recipientId(auth.getName())));
    }

    @PutMapping("/preferences")
    public ResponseEntity<?> savePreferences(Authentication auth, @RequestBody JsonNode body) {
        if (body == null || !body.isObject() || body.size() != NotificationType.values().length) return invalid();
        var preferences = new EnumMap<NotificationType, Boolean>(NotificationType.class);
        for (var type : NotificationType.values()) {
            JsonNode value = body.path(type.name());
            if (!value.isBoolean()) return invalid();
            preferences.put(type, value.booleanValue());
        }
        return noStore(notifications.savePreferences(notifications.recipientId(auth.getName()), preferences));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HandlerMethodValidationException.class,
        MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> invalid() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).cacheControl(CacheControl.noStore())
            .body(Map.of("error", "VALIDATION"));
    }

    private static <T> ResponseEntity<T> noStore(T result) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
