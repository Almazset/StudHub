package kz.studhub.calendar;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calendar")
class CalendarApiController {
    private final CalendarDeadlinesService calendar;

    CalendarApiController(CalendarDeadlinesService calendar) {
        this.calendar = calendar;
    }

    @GetMapping
    ResponseEntity<CalendarDeadlinesService.CalendarResponse> deadlines(Authentication user,
        @RequestParam(defaultValue = "week") String view,
        @RequestParam(required = false) String date,
        @RequestParam(required = false) String zone) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(calendar.calendar(user.getName(), view, date, zone));
    }

    @ExceptionHandler(CalendarWindow.InvalidParameter.class)
    ResponseEntity<Map<String, String>> invalid(CalendarWindow.InvalidParameter exception) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
            .body(Map.of("error", exception.getMessage()));
    }
}
