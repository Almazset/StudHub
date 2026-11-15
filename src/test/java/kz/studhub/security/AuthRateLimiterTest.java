package kz.studhub.security;

import java.time.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static kz.studhub.security.AuthRateLimiter.Action.*;

class AuthRateLimiterTest {
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-16T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    @Test void accountLimitSurvivesIpRotationAndCaseChangesAndExpiresWithoutExtension() {
        var clock = new MutableClock(); var limiter = new AuthRateLimiter(clock, 100);
        for (int i=0;i<10;i++) assertEquals(0, limiter.acquire(LOGIN, "ip-"+i, " Owner@Example.com "));
        assertEquals(900, limiter.acquire(LOGIN,"another", "owner@example.com"));
        clock.now=clock.now.plusSeconds(899);
        assertEquals(1, limiter.acquire(LOGIN,"another", "OWNER@example.com"));
        clock.now=clock.now.plusSeconds(1);
        assertEquals(0, limiter.acquire(LOGIN,"another", "owner@example.com"));
    }
    @Test void ipLimitSurvivesAccountRotationAndDoesNotBlockOtherClients() {
        var limiter = new AuthRateLimiter();
        for(int i=0;i<30;i++) assertEquals(0,limiter.acquire(LOGIN,"shared-ip","user"+i));
        assertTrue(limiter.acquire(LOGIN,"shared-ip","new")>0);
        assertEquals(0,limiter.acquire(LOGIN,"another-ip","new"));
        assertEquals(0,limiter.acquire(REGISTER,"shared-ip","new"));
    }
    @Test void registrationHasIndependentIpAndEmailLimits() {
        var limiter = new AuthRateLimiter();
        for(int i=0;i<3;i++) assertEquals(0,limiter.acquire(REGISTER,"ip"+i,"same@email.test"));
        assertTrue(limiter.acquire(REGISTER,"different","SAME@email.test")>0);
        for(int i=0;i<5;i++) assertEquals(0,limiter.acquire(REGISTER,"single-ip","different"+i));
        assertTrue(limiter.acquire(REGISTER,"single-ip","sixth")>0);
    }
    @Test void memoryCapacityFailsClosedAndExpiredEntriesAreReclaimed() {
        var clock=new MutableClock(); var limiter=new AuthRateLimiter(clock,2);
        assertEquals(0,limiter.acquire(LOGIN,"ip","user"));
        assertEquals(60,limiter.acquire(LOGIN,"new-ip","new-user"));
        for(int i=1;i<10;i++) assertEquals(0,limiter.acquire(LOGIN,"ip","user"));
        assertEquals(900,limiter.acquire(LOGIN,"ip","user"));
        clock.now=clock.now.plusSeconds(900);
        assertEquals(0,limiter.acquire(LOGIN,"new-ip","new-user"));
    }
    @Test void concurrentRequestsCannotOverrunTheAccountLimit() throws Exception {
        var limiter=new AuthRateLimiter();
        try(var pool=Executors.newFixedThreadPool(12)) {
            var jobs=IntStream.range(0,100).<Callable<Boolean>>mapToObj(i -> () -> limiter.acquire(LOGIN,"ip"+i,"same") == 0).toList();
            int admitted=0;
            for(var result:pool.invokeAll(jobs)) if(result.get()) admitted++;
            assertEquals(10,admitted);
        }
    }
}
