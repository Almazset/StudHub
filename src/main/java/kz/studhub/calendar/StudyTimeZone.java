package kz.studhub.calendar;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Set;

public final class StudyTimeZone {
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Almaty");
    private static final Set<String> AVAILABLE_ZONES = ZoneId.getAvailableZoneIds();

    private StudyTimeZone() {}

    public static ZoneId resolve(String requestedZone) {
        if (requestedZone == null) return DEFAULT_ZONE;
        if (!AVAILABLE_ZONES.contains(requestedZone)) throw new DateTimeException("Unknown time zone");
        return ZoneId.of(requestedZone);
    }
}
