package kz.studhub.web;

import jakarta.servlet.RequestDispatcher;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import kz.studhub.security.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AuthAbuseProtectionTest {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired TwoFactorService factors;
    String ip, email;
    static final String PASSWORD="Test-only-password!";
    @BeforeEach void identity() { ip="client-"+UUID.randomUUID(); email=UUID.randomUUID()+"@auth.invalid"; }
    MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request, String address) {
        return request.with(r -> { r.setRemoteAddr(address); return r; });
    }
    MockHttpServletRequestBuilder login(String address, String name, String password, String code) {
        return from(post("/login").with(csrf()).param("username",name).param("password",password).param("code",code), address);
    }
    UserAccount account() {
        var user=new UserAccount();user.setFullName("Auth test");user.setEmail(email);user.setPassword(encoder.encode(PASSWORD));
        return users.saveAndFlush(user);
    }
    @Test void loginLimitRejectsKnownAndUnknownAccountsAcrossSessionsAndIpsWithoutSecrets() throws Exception {
        account();
        for(String name:List.of(email,UUID.randomUUID()+"@auth.invalid")) {
            for(int i=0;i<10;i++) mvc.perform(login(ip+i,name,"wrong-secret-marker","otp-marker"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"));
            var blocked=mvc.perform(login(ip+"blocked",name.toUpperCase(Locale.ROOT),PASSWORD,"otp-marker"))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(content().string(containsString("Слишком много попыток"))).andReturn();
            assertThat(blocked.getResponse().getContentAsString()).doesNotContain(PASSWORD,name,"otp-marker","wrong-secret-marker","Exception");
            assertThat(blocked.getRequest().getSession(false)).isNull();
        }
    }
    @Test void forgedForwardedHeadersDoNotBypassIpLimit() throws Exception {
        for(int i=0;i<30;i++) mvc.perform(login(ip,UUID.randomUUID()+"@auth.invalid","wrong","")
            .header("X-Forwarded-For","203.0.113."+i).header("Forwarded","for=203.0.113."+i)).andExpect(status().is3xxRedirection());
        mvc.perform(login(ip,"another@auth.invalid","wrong","").header("X-Forwarded-For","different"))
            .andExpect(status().isTooManyRequests());
    }
    @Test void registrationInvalidAttemptsCountAndCsrfStillRequired() throws Exception {
        mvc.perform(from(post("/register"),ip)).andExpect(status().isForbidden());
        for(int i=0;i<5;i++) mvc.perform(from(post("/register").with(csrf()).param("email",i+email),ip))
            .andExpect(status().isOk()).andExpect(view().name("register"));
        mvc.perform(from(post("/register").with(csrf()).param("email","another"+email),ip))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(from(get("/register"),ip)).andExpect(status().isOk());
    }
    @Test void repeatedRegistrationEmailCannotBypassByChangingAddress() throws Exception {
        for(int i=0;i<3;i++) mvc.perform(from(post("/register").with(csrf()).param("email",email),ip+i)).andExpect(status().isOk());
        mvc.perform(from(post("/register").with(csrf()).param("email",email.toUpperCase(Locale.ROOT)),ip+"new"))
            .andExpect(status().isTooManyRequests());
    }
    @Test void twoFactorLockPersistsAndCorrectPasswordDoesNotBypassIt() throws Exception {
        var user=account();
        var recovery=factors.enable(email,Totp.newSecret(),Instant.now().getEpochSecond()/30);
        for(int i=0;i<7;i++) mvc.perform(login(ip,email,PASSWORD,"invalid"))
            .andExpect(redirectedUrl("/login?error"));
        mvc.perform(login(ip,email,PASSWORD,"invalid")).andExpect(status().isTooManyRequests());
        assertThat(users.findById(user.getId()).orElseThrow().getOtpLockedUntil()).isAfter(Instant.now());
        mvc.perform(login(ip+"new",email,PASSWORD,recovery.get(0))).andExpect(status().isTooManyRequests());
        assertThat(factors.verifyLogin(email,recovery.get(0))).isFalse();
        mvc.perform(from(post("/api/settings/password").with(user(email)).with(csrf()).contentType("application/json")
            .content("{\"currentPassword\":\""+PASSWORD+"\",\"newPassword\":\"New-test-password!\",\"confirmPassword\":\"New-test-password!\",\"code\":\"invalid\"}"),ip))
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error").value("settings.error.rateLimit"));
    }
    @Test void totpAndRecoveryCodesAreSingleUseThroughRealLogin() throws Exception {
        account();String secret=Totp.newSecret();long step=Instant.now().getEpochSecond()/30;
        var recovery=factors.enable(email,secret,step-2);
        String code=Totp.code(secret,step);
        var success=mvc.perform(login(ip,email,PASSWORD,code)).andExpect(redirectedUrl("/")).andReturn();
        var context=(org.springframework.security.core.context.SecurityContext) success.getRequest().getSession()
            .getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(context.getAuthentication().getCredentials()).isNull();
        assertThat(((LoginDetails)context.getAuthentication().getDetails()).getCode()).isEmpty();
        mvc.perform(login(ip,email,PASSWORD,code)).andExpect(redirectedUrl("/login?error"));
        mvc.perform(login(ip,email,PASSWORD,recovery.get(0))).andExpect(redirectedUrl("/"));
        mvc.perform(login(ip,email,PASSWORD,recovery.get(0))).andExpect(redirectedUrl("/login?error"));
    }
    @Test void settingsSecurityLimitUsesAccountAcrossSessionsAndReturnsJson() throws Exception {
        account();
        for(int i=0;i<20;i++) mvc.perform(from(post("/api/settings/two-factor/enable").with(user(email)).with(csrf())
            .contentType("application/json").content("{\"code\":\"invalid\"}"),ip+i)).andExpect(status().isBadRequest());
        mvc.perform(from(post("/api/settings/two-factor/start").with(user(email)).with(csrf())
            .contentType("application/json").content("{\"password\":\"private-marker\"}"),ip+"new"))
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error").value("settings.error.rateLimit"))
            .andExpect(jsonPath("$.retryAfter").isNumber()).andExpect(content().string(not(containsString("private-marker"))));
    }
    @Test void registrationAndErrorResponsesNeverEchoPasswordsOrInternalDetails() throws Exception {
        account();
        mvc.perform(from(post("/register").with(csrf()).param("fullName","Test member").param("email",email)
            .param("password",PASSWORD).param("confirmPassword",PASSWORD),ip))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Не удалось создать аккаунт")))
            .andExpect(content().string(not(containsString(PASSWORD))));
        String tooLong="я".repeat(40);
        mvc.perform(from(post("/register").with(csrf()).param("fullName","Test member").param("email","new"+email)
            .param("password",tooLong).param("confirmPassword",tooLong),ip))
            .andExpect(status().isOk()).andExpect(content().string(containsString("72 байта")))
            .andExpect(content().string(not(containsString(tooLong))));
        mvc.perform(get("/error").param("trace","true").param("message","true").accept("application/json")
            .requestAttr(RequestDispatcher.ERROR_STATUS_CODE,500)
            .requestAttr(RequestDispatcher.ERROR_EXCEPTION,new IllegalStateException("db-password-and-key-marker")))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.trace").doesNotExist())
            .andExpect(jsonPath("$.exception").doesNotExist()).andExpect(jsonPath("$.message").doesNotExist())
            .andExpect(content().string(not(containsString("db-password-and-key-marker"))));
    }
}
