package kz.studhub.assistant;

import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AssistantUsageLimiter {
    private static class Usage { Instant start = Instant.now(); Instant day = Instant.now(); int minuteCount; int dayCount; boolean busy; }
    private final Map<Long,Usage> accounts = new HashMap<>();
    private final Semaphore capacity = new Semaphore(4);
    public synchronized Lease acquire(Long accountId) {
        Instant now = Instant.now();
        accounts.entrySet().removeIf(entry -> !entry.getValue().busy && entry.getValue().day.plusSeconds(86400).isBefore(now));
        if (!accounts.containsKey(accountId) && accounts.size() >= 10_000) throw limited("BUSY");
        Usage usage = accounts.computeIfAbsent(accountId,ignored -> new Usage());
        if (usage.busy) throw limited("BUSY");
        if (usage.start.plusSeconds(60).isBefore(now)) { usage.start = now; usage.minuteCount = 0; }
        if (usage.day.plusSeconds(86400).isBefore(now)) { usage.day = now; usage.dayCount = 0; }
        if (usage.minuteCount >= 8 || usage.dayCount >= 40) throw limited("RATE_LIMIT");
        if (!capacity.tryAcquire()) throw limited("BUSY");
        usage.busy = true; usage.minuteCount++; usage.dayCount++;
        return new Lease(usage);
    }
    public final class Lease implements AutoCloseable {
        private final Usage usage; private boolean closed;
        private Lease(Usage usage) { this.usage = usage; }
        @Override public void close() { synchronized(AssistantUsageLimiter.this) { if (!closed) { closed = true; usage.busy = false; capacity.release(); } } }
    }
    private AssistantException limited(String code) { return new AssistantException(HttpStatus.TOO_MANY_REQUESTS,code); }
}
