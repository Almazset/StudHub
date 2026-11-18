package kz.studhub.calendar;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CalendarWindowTest {
    private static Clock utc(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @Test void theUsersZoneDeterminesTodayWithoutShiftingAStoredDeadline() {
        Clock moment = utc("2026-11-18T20:30:00Z");
        var east = CalendarWindow.resolve("day", null, "Asia/Qyzylorda", moment);
        var west = CalendarWindow.resolve("day", null, "America/Los_Angeles", moment);
        assertThat(east.today()).isEqualTo("2026-11-19");
        assertThat(west.today()).isEqualTo("2026-11-18");

        LocalDate deadline = LocalDate.parse("2026-11-18");
        assertThat(CalendarWindow.resolve("day", deadline.toString(), "Asia/Qyzylorda", moment).date())
            .isEqualTo(deadline);
        assertThat(CalendarDeadlinesService.status(deadline, false, east.today())).isEqualTo("overdue");
        assertThat(CalendarDeadlinesService.status(deadline, false, west.today())).isEqualTo("today");
        assertThat(CalendarDeadlinesService.status(deadline.plusDays(3), false, west.today())).isEqualTo("soon");
        assertThat(CalendarDeadlinesService.status(deadline.plusDays(4), false, west.today())).isEqualTo("upcoming");
        assertThat(CalendarDeadlinesService.status(deadline, true, east.today())).isEqualTo("done");
    }

    @Test void weeksUseCalendarDaysAcrossDaylightSavingAndYearBoundaries() {
        var week = CalendarWindow.resolve("week", "2026-03-08", "America/New_York", utc("2026-03-09T03:59:59Z"));
        var next = CalendarWindow.resolve("week", null, "America/New_York", utc("2026-03-09T04:00:00Z"));
        assertThat(week.today()).isEqualTo("2026-03-08");
        assertThat(week.from()).isEqualTo("2026-03-02");
        assertThat(week.to()).isEqualTo("2026-03-08");
        assertThat(next.today()).isEqualTo("2026-03-09");
        assertThat(next.from()).isEqualTo("2026-03-09");
        assertThat(next.to()).isEqualTo("2026-03-15");

        var newYear = CalendarWindow.resolve("week", "2026-01-01", "UTC", utc("2026-01-01T00:00:00Z"));
        assertThat(newYear.from()).isEqualTo("2025-12-29");
        assertThat(newYear.to()).isEqualTo("2026-01-04");
        assertThat(CalendarWindow.resolve("week", "9999-12-31", "UTC", utc("2026-01-01T00:00:00Z")).to())
            .isEqualTo("9999-12-31");
    }

    @Test void leapMonthsAndInvalidValuesAreHandledStrictly() {
        Clock moment = utc("2024-02-29T00:00:00Z");
        var month = CalendarWindow.resolve("month", "2024-02-29", null, moment);
        assertThat(month.from()).isEqualTo("2024-02-01");
        assertThat(month.to()).isEqualTo("2024-02-29");

        assertThatThrownBy(() -> CalendarWindow.resolve("month", "2025-02-29", "UTC", moment))
            .hasMessage("INVALID_CALENDAR_DATE");
        assertThatThrownBy(() -> CalendarWindow.resolve("month", "0000-01-01", "UTC", moment))
            .hasMessage("INVALID_CALENDAR_DATE");
        assertThatThrownBy(() -> CalendarWindow.resolve("month", null, "+05:00", moment))
            .hasMessage("INVALID_TIME_ZONE");
        assertThatThrownBy(() -> CalendarWindow.resolve("year", null, "UTC", moment))
            .hasMessage("INVALID_CALENDAR_VIEW");
    }
}
