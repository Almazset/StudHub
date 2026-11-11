package kz.studhub.friends;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PresenceService {
    static final Duration TIMEOUT=Duration.ofSeconds(75);
    private final PresencePreferenceRepository preferences;
    private final Clock clock;
    private final Map<Long,Instant> activity=new ConcurrentHashMap<>();
    private final AtomicLong nextCleanup=new AtomicLong();
    @Autowired
    public PresenceService(PresencePreferenceRepository preferences) { this(preferences,Clock.systemUTC()); }
    PresenceService(PresencePreferenceRepository preferences,Clock clock) { this.preferences=preferences;this.clock=clock; }

    public void heartbeat(Long userId) {
        Instant now=clock.instant();activity.put(userId,now);
        long next=nextCleanup.get();
        if(now.toEpochMilli()>=next && nextCleanup.compareAndSet(next,now.plusSeconds(60).toEpochMilli()))
            activity.entrySet().removeIf(e -> !e.getValue().plus(TIMEOUT).isAfter(now));
    }
    @Transactional(readOnly=true)
    public String preference(Long userId) { return preferences.findById(userId).map(p -> p.status).orElse("ONLINE"); }
    @Transactional
    public String choose(Long userId,String status) {
        preferences.save(new PresencePreference(userId,status));
        heartbeat(userId);return status;
    }
    // Only call with accepted friends. The API deliberately has no arbitrary-user status lookup.
    @Transactional(readOnly=true)
    public Map<Long,String> visibleStatuses(Collection<Long> ids) {
        Map<Long,String> selected=new HashMap<>(),result=new HashMap<>();
        preferences.findAllById(ids).forEach(p -> selected.put(p.userId,p.status));
        Instant now=clock.instant();
        for(Long id:ids) {
            String status=selected.getOrDefault(id,"ONLINE");Instant last=activity.get(id);
            result.put(id,last==null || !last.plus(TIMEOUT).isAfter(now) || status.equals("INVISIBLE") ? "OFFLINE" : status);
        }
        return result;
    }
}
