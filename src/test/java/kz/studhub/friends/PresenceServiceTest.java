package kz.studhub.friends;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PresenceServiceTest {
 @Test void expiresActivityAndHidesInvisibleFriends() {
  var repo=mock(PresencePreferenceRepository.class);var clock=mock(Clock.class);
  Instant now=Instant.parse("2026-11-11T00:00:00Z");when(clock.instant()).thenReturn(now);
  when(repo.findAllById(Set.of(1L,2L,3L))).thenReturn(List.of(new PresencePreference(2L,"INVISIBLE"),new PresencePreference(3L,"BUSY")));
  var privacy=mock(ContactPrivacyService.class);
  when(privacy.presencePeers(9L,List.of(1L,2L,3L))).thenReturn(Set.of(1L,2L,3L));
  var presence=new PresenceService(repo,privacy,clock);
  presence.heartbeat(1L);presence.heartbeat(2L);presence.heartbeat(3L);
  assertThat(presence.visibleStatuses(9L,List.of(1L,2L,3L))).containsEntry(1L,"ONLINE").containsEntry(2L,"OFFLINE").containsEntry(3L,"BUSY");
  when(clock.instant()).thenReturn(now.plusSeconds(75));
  assertThat(presence.visibleStatuses(9L,List.of(1L,2L,3L)).values()).containsOnly("OFFLINE");
  presence.heartbeat(3L);
  assertThat(presence.visibleStatuses(9L,List.of(1L,2L,3L))).containsEntry(1L,"OFFLINE").containsEntry(3L,"BUSY");
 }
 @Test void savedPreferenceDoesNotMakeInactiveUserOnline() {
  var repo=mock(PresencePreferenceRepository.class);when(repo.findAllById(Set.of(4L))).thenReturn(List.of(new PresencePreference(4L,"AWAY")));
  var privacy=mock(ContactPrivacyService.class);
  when(privacy.presencePeers(9L,List.of(4L))).thenReturn(Set.of(4L));
  var presence=new PresenceService(repo,privacy,Clock.systemUTC());
  assertThat(presence.visibleStatuses(9L,List.of(4L))).containsEntry(4L,"OFFLINE");
 }
}
