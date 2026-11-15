package kz.studhub.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.*;

/** Single-instance admission limits. Never evict live counters to admit new identities. */
public class AuthRateLimiter {
    public enum Action {
        LOGIN(30, 10, 900), REGISTER(5, 3, 3600), SECURITY(40, 20, 900);
        final int perIp, perAccount, seconds;
        Action(int perIp, int perAccount, int seconds) {
            this.perIp = perIp; this.perAccount = perAccount; this.seconds = seconds;
        }
    }
    private record Window(int count, long expires) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    private final int capacity;
    private long nextCleanup;
    public AuthRateLimiter() { this(Clock.systemUTC(), 20000); }
    AuthRateLimiter(Clock clock, int capacity) { this.clock = clock; this.capacity = capacity; }

    /** Returns zero on admission, otherwise seconds until another attempt is allowed. */
    public synchronized long acquire(Action action, String ip, String account) {
        long now = clock.millis();
        if (now >= nextCleanup || windows.size() >= capacity) {
            windows.values().removeIf(w -> w.expires <= now);
            nextCleanup = now + 60000;
        }
        String ipKey = action + ":ip:" + digest(ip == null ? "unknown" : ip);
        String normalized = account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 120) normalized = "invalid-account";
        String accountKey = action + ":account:" + digest(normalized);
        Window ipWindow = current(ipKey, now), accountWindow = current(accountKey, now);
        long retry = Math.max(waitFor(ipWindow, action.perIp, now), waitFor(accountWindow, action.perAccount, now));
        if (retry > 0) return retry;
        int needed = (ipWindow == null ? 1 : 0) + (accountWindow == null ? 1 : 0);
        if (windows.size() + needed > capacity) return 60;
        windows.put(ipKey, increment(ipWindow, now, action.seconds));
        windows.put(accountKey, increment(accountWindow, now, action.seconds));
        return 0;
    }
    private Window current(String key, long now) {
        Window w = windows.get(key);
        if (w != null && w.expires <= now) { windows.remove(key); return null; }
        return w;
    }
    private static Window increment(Window w, long now, int seconds) {
        return w == null ? new Window(1, now + seconds * 1000L) : new Window(w.count + 1, w.expires);
    }
    private static long waitFor(Window w, int limit, long now) {
        return w != null && w.count >= limit ? Math.max(1, (w.expires - now + 999) / 1000) : 0;
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
