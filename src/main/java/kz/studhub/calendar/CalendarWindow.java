package kz.studhub.calendar;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Pattern;

record CalendarWindow(String view, LocalDate date, LocalDate today, ZoneId zone, LocalDate from, LocalDate to) {
    private static final LocalDate FIRST_DATE = LocalDate.of(1, 1, 1);
    private static final LocalDate LAST_DATE = LocalDate.of(9999, 12, 31);
    private static final Pattern DATE_FORMAT = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    static CalendarWindow resolve(String view, String date, String requestedZone, Clock clock) {
        ZoneId zone;
        try {
            zone = StudyTimeZone.resolve(requestedZone);
        } catch (DateTimeException ex) {
            throw new InvalidParameter("INVALID_TIME_ZONE");
        }
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate anchor = date == null ? today : parseDate(date);
        return switch (view) {
            case "day" -> new CalendarWindow(view, anchor, today, zone, anchor, anchor);
            case "week" -> {
                LocalDate monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate sunday = monday.plusDays(6);
                LocalDate first = monday.isBefore(FIRST_DATE) ? FIRST_DATE : monday;
                LocalDate last = sunday.isAfter(LAST_DATE) ? LAST_DATE : sunday;
                yield new CalendarWindow(view, anchor, today, zone, first, last);
            }
            case "month" -> new CalendarWindow(view, anchor, today, zone,
                anchor.withDayOfMonth(1), anchor.with(TemporalAdjusters.lastDayOfMonth()));
            default -> throw new InvalidParameter("INVALID_CALENDAR_VIEW");
        };
    }

    private static LocalDate parseDate(String input) {
        if (!DATE_FORMAT.matcher(input).matches()) throw new InvalidParameter("INVALID_CALENDAR_DATE");
        try {
            LocalDate date = LocalDate.parse(input);
            if (date.isBefore(FIRST_DATE)) throw new InvalidParameter("INVALID_CALENDAR_DATE");
            return date;
        } catch (DateTimeParseException ex) {
            throw new InvalidParameter("INVALID_CALENDAR_DATE");
        }
    }

    static final class InvalidParameter extends RuntimeException {
        InvalidParameter(String code) { super(code); }
    }
}
