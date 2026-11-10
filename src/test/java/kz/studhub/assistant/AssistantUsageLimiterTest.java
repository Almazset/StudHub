package kz.studhub.assistant;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssistantUsageLimiterTest {
    @Test void limitsConcurrentCallsAndReleasesCapacityAfterErrors() {
        var limiter = new AssistantUsageLimiter();
        var first = limiter.acquire(1L);
        assertEquals("BUSY",assertThrows(AssistantException.class,() -> limiter.acquire(1L)).code());
        var second = limiter.acquire(2L); var third = limiter.acquire(3L); var fourth = limiter.acquire(4L);
        assertEquals("BUSY",assertThrows(AssistantException.class,() -> limiter.acquire(5L)).code());
        first.close(); first.close();
        try (var fifth = limiter.acquire(5L)) { assertNotNull(fifth); }
        second.close(); third.close(); fourth.close();
    }
    @Test void enforcesPerAccountRequestLimitWithoutBlockingOtherAccounts() {
        var limiter = new AssistantUsageLimiter();
        for (int i=0;i<8;i++) try (var lease = limiter.acquire(1L)) { assertNotNull(lease); }
        assertEquals("RATE_LIMIT",assertThrows(AssistantException.class,() -> limiter.acquire(1L)).code());
        try (var lease = limiter.acquire(2L)) { assertNotNull(lease); }
    }
}
