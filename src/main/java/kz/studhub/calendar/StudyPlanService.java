package kz.studhub.calendar;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyPlanService {
    private static final int MAX_ITEMS = 500;
    private static final int MAX_RANGE_DAYS = 62;
    private static final String DATE_PATTERN = "[0-9]{4}-[0-9]{2}-[0-9]{2}";
    private final StudyPlanRepository plans;
    private final UserAccountRepository users;

    public record Input(
        @NotBlank @Size(max = StudyPlanItem.TITLE_LENGTH) String title,
        @NotBlank @Pattern(regexp = DATE_PATTERN) String date,
        @NotNull @Min(StudyPlanItem.MIN_MINUTES) @Max(StudyPlanItem.MAX_MINUTES) Integer minutes,
        @Size(max = StudyPlanItem.DESCRIPTION_LENGTH) String description,
        boolean completed
    ) {}

    public record Entry(Long id, String title, LocalDate date, int minutes, String description, boolean completed) {}

    public static class Failure extends RuntimeException {
        private final HttpStatus status;

        Failure(HttpStatus status, String code) {
            super(code);
            this.status = status;
        }

        public HttpStatus status() {
            return status;
        }
    }

    StudyPlanService(StudyPlanRepository plans, UserAccountRepository users) {
        this.plans = plans;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<Entry> list(String email, String from, String to) {
        LocalDate start = parseDate(from, "INVALID_RANGE");
        LocalDate end = parseDate(to, "INVALID_RANGE");
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
            throw new Failure(HttpStatus.BAD_REQUEST, "INVALID_RANGE");
        }
        long ownerId = users.findByEmailIgnoreCase(email).orElseThrow(StudyPlanService::unauthorized).getId();
        return plans.findByOwnerIdAndDateBetweenOrderByDateAscIdAsc(ownerId, start, end)
            .stream().map(StudyPlanService::view).toList();
    }

    @Transactional
    public Entry create(String email, Input input) {
        UserAccount owner = lockedOwner(email);
        if (plans.countByOwnerId(owner.getId()) >= MAX_ITEMS) {
            throw new Failure(HttpStatus.CONFLICT, "PLAN_LIMIT");
        }
        var item = new StudyPlanItem(owner);
        apply(item, input);
        return view(plans.save(item));
    }

    @Transactional
    public Entry update(String email, long id, Input input) {
        var item = ownedItem(id, lockedOwner(email).getId());
        apply(item, input);
        return view(item);
    }

    @Transactional
    public void delete(String email, long id) {
        plans.delete(ownedItem(id, lockedOwner(email).getId()));
    }

    // All edits serialize on the account row, including the limit check and insert.
    private UserAccount lockedOwner(String email) {
        return users.findLockedByEmail(email).orElseThrow(StudyPlanService::unauthorized);
    }

    private StudyPlanItem ownedItem(long id, long ownerId) {
        return plans.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(() -> new Failure(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND"));
    }

    private static void apply(StudyPlanItem item, Input input) {
        item.title = clean(input.title());
        item.description = input.description() == null ? "" : clean(input.description());
        item.date = parseDate(input.date(), "VALIDATION");
        item.minutes = input.minutes();
        item.completed = input.completed();
    }

    private static String clean(String value) {
        if (value.indexOf(0) >= 0) throw new Failure(HttpStatus.BAD_REQUEST, "VALIDATION");
        return value.strip();
    }

    private static LocalDate parseDate(String value, String errorCode) {
        if (value != null && value.matches(DATE_PATTERN)) {
            try {
                LocalDate date = LocalDate.parse(value);
                if (date.getYear() > 0) return date;
            } catch (DateTimeParseException ignored) {
                // The same error covers any malformed or nonexistent date.
            }
        }
        throw new Failure(HttpStatus.BAD_REQUEST, errorCode);
    }

    private static Entry view(StudyPlanItem item) {
        return new Entry(item.id, item.title, item.date, item.minutes, item.description, item.completed);
    }

    private static Failure unauthorized() {
        return new Failure(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }
}
