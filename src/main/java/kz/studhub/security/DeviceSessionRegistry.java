package kz.studhub.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.session.SessionRegistryImpl;

public class DeviceSessionRegistry extends SessionRegistryImpl {
    public record Device(String label, Instant createdAt) {}
    private final Map<String, Device> devices = new ConcurrentHashMap<>();

    public void recordDevice(String sessionId, String userAgent) {
        String ua = userAgent == null ? "" : userAgent;
        String browser = ua.contains("Edg/") ? "Edge" : ua.contains("Firefox/") ? "Firefox" : ua.contains("Chrome/") ? "Chrome" : ua.contains("Safari/") ? "Safari" : "Browser";
        String os = ua.contains("Android") ? "Android" : ua.contains("iPhone") || ua.contains("iPad") ? "iOS" : ua.contains("Windows") ? "Windows" : ua.contains("Macintosh") ? "macOS" : ua.contains("Linux") ? "Linux" : "Unknown OS";
        devices.put(sessionId, new Device(browser + " · " + os, Instant.now()));
    }
    public Device device(String sessionId) { return devices.getOrDefault(sessionId, new Device("Browser", Instant.now())); }
    @Override public void removeSessionInformation(String sessionId) { super.removeSessionInformation(sessionId); devices.remove(sessionId); }
}
