package kz.studhub.web;

import kz.studhub.api.*;
import kz.studhub.config.SecurityConfig;
import kz.studhub.model.*;
import kz.studhub.repository.*;
import kz.studhub.security.*;
import kz.studhub.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({SettingsApiController.class,MemberProfileController.class,SettingsPageController.class})
@Import(SecurityConfig.class)
class SettingsSecurityMvcTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @MockitoBean AccountSettingsService settings;
    @MockitoBean TwoFactorService twoFactor;
    @MockitoBean AccountSessions sessions;
    @MockitoBean ProfilePhotoService photos;
    @MockitoBean SupportRequestRepository support;
    @MockitoBean UserAccountRepository users;
    @MockitoBean AssignmentService assignments;
    @MockitoBean CustomUserDetailsService userDetails;
    private UserAccount owner;
    @BeforeEach void setup() {
        owner = new UserAccount(); owner.setId(1L); owner.setFullName("Test Owner"); owner.setEmail("owner@example.com");
        when(settings.account(owner.getEmail())).thenReturn(owner);
        when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
        when(assignments.findAll()).thenReturn(List.of());
        when(support.findTop20ByOwnerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(sessions.list(eq(owner.getEmail()),anyString())).thenReturn(List.of());
    }
    @Test void settingsRequireAuthenticationAndCsrfAndValidatePreferences() throws Exception {
        mvc.perform(get("/api/settings")).andExpect(status().is3xxRedirection());
        mvc.perform(put("/api/settings/appearance").with(user(owner.getEmail())).contentType(MediaType.APPLICATION_JSON).content("{\"theme\":\"light\",\"fontSize\":\"large\",\"language\":\"en\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/settings/appearance").with(user(owner.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"theme\":\"invalid\",\"fontSize\":\"large\",\"language\":\"en\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/settings/appearance").with(user(owner.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"theme\":\"light\",\"fontSize\":\"large\",\"language\":\"en\"}")).andExpect(status().isOk());
        verify(settings).appearance(owner.getEmail(),"light","large","en");
    }
    @Test void settingsTemplateRendersAndResponseContainsNoSecrets() throws Exception {
        owner.setTwoFactorSecret("encrypted-secret"); owner.setPassword("password-hash"); owner.setRecoveryCodeHashes("recovery-hash");
        mvc.perform(get("/settings").with(user(owner.getEmail()))).andExpect(status().isOk()).andExpect(content().string(containsString("data-settings-root")));
        var response = mvc.perform(get("/api/settings").with(user(owner.getEmail()))).andExpect(status().isOk()).andExpect(header().string("Cache-Control",containsString("no-store"))).andReturn().getResponse().getContentAsString();
        Assertions.assertFalse(response.contains("encrypted-secret")); Assertions.assertFalse(response.contains("password-hash")); Assertions.assertFalse(response.contains("recovery-hash"));
    }
    @Test void privateProfileIsNotVisibleToAnotherMemberAndEmailIsNeverInPublicView() throws Exception {
        var target = new UserAccount(); target.setId(2L); target.setFullName("Another Member"); target.setEmail("private@example.com");
        when(users.findById(2L)).thenReturn(Optional.of(target));
        mvc.perform(get("/api/profiles/2").with(user(owner.getEmail()))).andExpect(status().isNotFound());
        target.setProfileVisibility("members");
        mvc.perform(get("/api/profiles/2").with(user(owner.getEmail()))).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Another Member")).andExpect(jsonPath("$.email").doesNotExist());
    }
    @Test void wrongSecondFactorPreventsPasswordChangeAndSupportInboxIsAdminOnly() throws Exception {
        when(twoFactor.verifyLogin(eq(owner.getEmail()),any())).thenReturn(false);
        mvc.perform(post("/api/settings/password").with(user(owner.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"oldpassword\",\"newPassword\":\"newpassword\",\"confirmPassword\":\"newpassword\",\"code\":\"000000\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("settings.error.code"));
        verify(settings,never()).changePassword(anyString(),anyString(),anyString(),anyString());
        mvc.perform(get("/api/settings/support/inbox").with(user(owner.getEmail()))).andExpect(status().isForbidden());
    }
    @Test void loginNeverCreatesAuthenticatedSessionWithoutRequiredSecondFactor() throws Exception {
        when(userDetails.loadUserByUsername(owner.getEmail())).thenReturn(User.withUsername(owner.getEmail()).password(encoder.encode("password123")).roles("USER").build());
        when(twoFactor.verifyLogin(owner.getEmail(),"")).thenReturn(false);
        when(twoFactor.verifyLogin(owner.getEmail(),"123456")).thenReturn(true);
        mvc.perform(post("/login").with(csrf()).param("username",owner.getEmail()).param("password","password123"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"));
        verify(twoFactor,times(1)).verifyLogin(owner.getEmail(),"");
        mvc.perform(post("/login").with(csrf()).param("username",owner.getEmail()).param("password","password123").param("code","123456"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
    }
    @Test void passwordChangeExpiresOtherSessionsAndSessionRevocationIsScoped() throws Exception {
        when(twoFactor.verifyLogin(owner.getEmail(),null)).thenReturn(true);
        mvc.perform(post("/api/settings/password").with(user(owner.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"oldpassword\",\"newPassword\":\"newpassword\",\"confirmPassword\":\"newpassword\"}"))
            .andExpect(status().isOk());
        verify(settings).changePassword(owner.getEmail(),"oldpassword","newpassword","newpassword");
        verify(sessions).revokeOthers(eq(owner.getEmail()),anyString());
        when(sessions.revoke(eq(owner.getEmail()),eq("foreign-session"),anyString())).thenReturn(false);
        mvc.perform(delete("/api/settings/sessions/foreign-session").with(user(owner.getEmail())).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void supportRejectsShortMessagesAndDoesNotAllowMembersToCloseRequests() throws Exception {
        mvc.perform(post("/api/settings/support").with(user(owner.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\"Help\",\"message\":\"short\"}"))
            .andExpect(status().isBadRequest());
        verify(support,never()).save(any());
        mvc.perform(post("/api/settings/support/1/close").with(user(owner.getEmail())).with(csrf())).andExpect(status().isForbidden());
        when(support.save(any())).thenAnswer(invocation -> {
            SupportRequest request = invocation.getArgument(0);
            var id = SupportRequest.class.getDeclaredField("id"); id.setAccessible(true); id.set(request,1L); return request;
        });
        mvc.perform(post("/api/settings/support").with(user(owner.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\"Settings help\",\"message\":\"A detailed test question\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1)).andExpect(jsonPath("$.closed").value(false));
    }
    @Test void cannotEnableTwoFactorWithoutConfirmedEnrollmentOrPassword() throws Exception {
        mvc.perform(post("/api/settings/two-factor/enable").with(user(owner.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("settings.error.enrollmentExpired"));
        verify(twoFactor,never()).enable(anyString(),anyString(),anyLong());
        doThrow(new IllegalArgumentException("settings.error.password")).when(settings).checkPassword(owner.getEmail(),"wrong");
        mvc.perform(post("/api/settings/two-factor/start").with(user(owner.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("settings.error.password"));
    }
    @Test void twoFactorEnrollmentRequiresMatchingCodeAndCannotBeSubmittedTwice() throws Exception {
        var session = new org.springframework.mock.web.MockHttpSession();
        var start = mvc.perform(post("/api/settings/two-factor/start").session(session).with(user(owner.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"currentpassword\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control",containsString("no-store"))).andReturn();
        String secret = mapper.readTree(start.getResponse().getContentAsString()).get("secret").asText();
        String code = Totp.code(secret,java.time.Instant.now().getEpochSecond()/30);
        when(twoFactor.enable(eq(owner.getEmail()),eq(secret),anyLong())).thenReturn(List.of("one-time-recovery"));
        mvc.perform(post("/api/settings/two-factor/enable").session(session).with(user(owner.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\""+code+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.recoveryCodes[0]").value("one-time-recovery"));
        verify(twoFactor,times(1)).enable(eq(owner.getEmail()),eq(secret),anyLong());
        verify(sessions).revokeOthers(owner.getEmail(),session.getId());
        mvc.perform(post("/api/settings/two-factor/enable").session(session).with(user(owner.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\""+code+"\"}"))
            .andExpect(status().isBadRequest());
    }
}
