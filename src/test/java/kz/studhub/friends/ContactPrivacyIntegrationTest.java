package kz.studhub.friends;

import java.time.Instant;
import java.util.UUID;
import kz.studhub.chat.ChatService;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:contact-privacy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "studhub.security.key-file=target/contact-privacy-test-security.key"
})
@AutoConfigureMockMvc
class ContactPrivacyIntegrationTest {
    private static final String BLOCKS = "/api/friends/blocked";
    private static final String REPORTS = "/api/friends/reports";
    private static final String PRIVACY = "/api/settings/privacy";

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired FriendshipRepository friendships;
    @Autowired ContactPrivacyService privacy;
    @Autowired PresenceService presence;
    @Autowired ChatService chat;
    @Autowired UserReportRepository reports;
    private UserAccount alice;
    private UserAccount bob;
    private UserAccount stranger;

    @BeforeEach void createUsers() {
        reports.deleteAll();
        alice = account("Alice", "members");
        bob = account("Bob", "members");
        stranger = account("Stranger", "private");
    }

    @Test void privacyChoicesApplyToProfilesAndPresenceIndependently() throws Exception {
        String bobProfile = "/api/profiles/" + bob.getId();
        mvc.perform(put(PRIVACY).with(user(bob.getEmail())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"friends\",\"directMessages\":\"friends\",\"presenceVisibility\":\"private\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/settings").with(user(bob.getEmail())))
            .andExpect(jsonPath("$.privacy.visibility").value("friends"))
            .andExpect(jsonPath("$.privacy.directMessages").value("friends"))
            .andExpect(jsonPath("$.privacy.presenceVisibility").value("private"));
        mvc.perform(put(PRIVACY).with(user(bob.getEmail())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"directMessages\":\"anything\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put(PRIVACY).with(user(bob.getEmail())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());

        mvc.perform(get(bobProfile).with(user(alice.getEmail()))).andExpect(status().isNotFound());
        mvc.perform(get(bobProfile + "/photo").with(user(alice.getEmail()))).andExpect(status().isNotFound());
        assertThat(privacy.canSendDirect(alice.getId(), bob.getId())).isFalse();

        befriend(alice, bob);
        mvc.perform(get(bobProfile).with(user(alice.getEmail())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Bob nickname"))
            .andExpect(jsonPath("$.email").doesNotExist());
        mvc.perform(get(bobProfile + "/photo").with(user(alice.getEmail()))).andExpect(status().isOk());
        mvc.perform(get(bobProfile).with(user(stranger.getEmail()))).andExpect(status().isNotFound());
        assertThat(privacy.canSendDirect(alice.getId(), bob.getId())).isTrue();

        presence.heartbeat(bob.getId());
        var requested = java.util.List.of(bob.getId());
        assertThat(presence.visibleStatuses(alice.getId(), requested)).isEmpty();
        mvc.perform(put(PRIVACY).with(user(bob.getEmail())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"presenceVisibility\":\"friends\"}"))
            .andExpect(status().isOk());
        assertThat(presence.visibleStatuses(alice.getId(), requested)).containsEntry(bob.getId(), "ONLINE");
        assertThat(presence.visibleStatuses(stranger.getId(), requested)).isEmpty();
        mvc.perform(get("/api/settings").with(user(bob.getEmail())))
            .andExpect(jsonPath("$.privacy.visibility").value("friends"))
            .andExpect(jsonPath("$.privacy.directMessages").value("friends"))
            .andExpect(jsonPath("$.privacy.presenceVisibility").value("friends"));
    }

    @Test void blockRemovesFriendshipAndMasksBothAccountsUntilOwnerUnblocks() throws Exception {
        befriend(alice, bob);
        String blockingBob = BLOCKS + "/" + bob.getId();
        mvc.perform(put(blockingBob).with(user(alice.getEmail()))).andExpect(status().isForbidden());
        mvc.perform(put(BLOCKS + "/" + stranger.getId()).with(user(alice.getEmail())).with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(put(BLOCKS + "/9999999").with(user(alice.getEmail())).with(csrf()))
            .andExpect(status().isNotFound());

        mvc.perform(put(blockingBob).with(user(alice.getEmail())).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(put(blockingBob).with(user(alice.getEmail())).with(csrf())).andExpect(status().isNoContent());
        assertThat(privacy.areFriends(alice.getId(), bob.getId())).isFalse();
        assertThat(privacy.canSendDirect(alice.getId(), bob.getId())).isFalse();
        assertThat(privacy.canSendDirect(bob.getId(), alice.getId())).isFalse();
        mvc.perform(get(BLOCKS).with(user(alice.getEmail())))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.users.length()").value(1))
            .andExpect(jsonPath("$.users[0].publicId").value("SH-" + bob.getId()))
            .andExpect(jsonPath("$.users[0].name").doesNotExist());
        mvc.perform(get(BLOCKS).with(user(bob.getEmail())))
            .andExpect(jsonPath("$.users.length()").value(0));
        mvc.perform(get("/api/profiles/" + alice.getId() + "/photo").with(user(bob.getEmail())))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/profiles/" + bob.getId()).with(user(alice.getEmail())))
            .andExpect(status().isNotFound());

        mvc.perform(delete(BLOCKS + "/" + alice.getId()).with(user(bob.getEmail())).with(csrf()))
            .andExpect(status().isNoContent());
        assertThat(privacy.isBlocked(alice.getId(), bob.getId())).isTrue();
        mvc.perform(delete(blockingBob).with(user(alice.getEmail())).with(csrf())).andExpect(status().isNoContent());
        assertThat(privacy.isBlocked(alice.getId(), bob.getId())).isFalse();
        assertThat(privacy.areFriends(alice.getId(), bob.getId())).isFalse();
    }

    @Test void onlyAdministratorsCanReviewReportsAndSubmissionsAreRateLimited() throws Exception {
        String reportBob = REPORTS + "/" + bob.getId();
        mvc.perform(post(reportBob).with(user(alice.getEmail()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post(reportBob).with(user(alice.getEmail())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(REPORTS + "/" + alice.getId()).with(user(alice.getEmail())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
            .andExpect(status().isBadRequest());
        for (int i = 0; i < 5; i++) {
            mvc.perform(post(reportBob).with(user(alice.getEmail())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"SPAM\",\"details\":\"test report " + i + "\"}"))
                .andExpect(status().isCreated());
        }
        mvc.perform(post(reportBob).with(user(alice.getEmail())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"OTHER\"}"))
            .andExpect(status().isTooManyRequests());
        mvc.perform(post(reportBob).with(user(alice.getEmail())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"SPAM\",\"details\":\" test report 4 \"}"))
            .andExpect(status().isCreated());
        mvc.perform(get(REPORTS).with(user(bob.getEmail()))).andExpect(status().isForbidden());
        mvc.perform(get(REPORTS).with(user(stranger.getEmail()).roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$[0].reporterId").value(alice.getId()))
            .andExpect(jsonPath("$[0].reportedId").value(bob.getId()))
            .andExpect(jsonPath("$[0].reason").value("SPAM"))
            .andExpect(jsonPath("$[0].details").value("test report 4"));
    }

    @Test void aSharedMessageLetsItsViewersActOnAnOtherwiseHiddenAuthor() throws Exception {
        String room = chat.group(alice.getEmail(), "Privacy trial", java.util.Set.of(bob.getId())).id();
        bob.setProfileVisibility("private");
        users.saveAndFlush(bob);
        long message = chat.send(bob.getEmail(), room, "Example group message", UUID.randomUUID()).id();
        long otherAuthor = chat.send(alice.getEmail(), room, "Another group message", UUID.randomUUID()).id();
        String blockBob = BLOCKS + "/" + bob.getId();

        mvc.perform(put(blockBob).with(user(stranger.getEmail())).with(csrf())
                .param("messageId", String.valueOf(message))).andExpect(status().isNotFound());
        mvc.perform(put(blockBob).with(user(alice.getEmail())).with(csrf())
                .param("messageId", String.valueOf(otherAuthor))).andExpect(status().isNotFound());
        mvc.perform(post(REPORTS + "/" + bob.getId()).with(user(alice.getEmail())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"HARASSMENT\",\"messageId\":" + message + "}"))
            .andExpect(status().isCreated());
        mvc.perform(put(blockBob).with(user(alice.getEmail())).with(csrf())
                .param("messageId", String.valueOf(message))).andExpect(status().isNoContent());
        assertThat(privacy.isBlocked(alice.getId(), bob.getId())).isTrue();
    }

    private UserAccount account(String name, String visibility) {
        UserAccount account = new UserAccount();
        account.setFullName(name + " Person");
        account.setNickname(name + " nickname");
        account.setEmail(name + "-" + UUID.randomUUID() + "@example.invalid");
        account.setPassword("not-a-real-encoded-password");
        account.setProfileVisibility(visibility);
        account.setPhoto(new byte[]{1, 2, 3});
        return users.saveAndFlush(account);
    }

    @Test void aFriendCannotOpenAChatAfterTheRecipientDisablesDirectMessages() throws Exception {
        var friendship = befriend(alice, bob);
        mvc.perform(put(PRIVACY).with(user(bob.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"directMessages\":\"nobody\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/friends/" + friendship.id + "/chat").with(user(alice.getEmail())).with(csrf()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("DIRECT_UNAVAILABLE"));
    }

    private Friendship befriend(UserAccount earlier, UserAccount later) {
        Friendship friendship = new Friendship();
        friendship.lowUserId = earlier.getId();
        friendship.highUserId = later.getId();
        friendship.requesterId = earlier.getId();
        friendship.status = "ACCEPTED";
        friendship.createdAt = Instant.now();
        return friendships.saveAndFlush(friendship);
    }
}
