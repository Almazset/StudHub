package kz.studhub.web;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import kz.studhub.api.AssistantApiController;
import kz.studhub.assistant.*;
import kz.studhub.config.SecurityConfig;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import kz.studhub.security.TwoFactorService;
import kz.studhub.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AssistantApiController.class)
@Import({SecurityConfig.class,AssistantUsageLimiter.class})
class AssistantApiSecurityTest {
    private static final AtomicLong ACCOUNT_IDS = new AtomicLong();
    @Autowired MockMvc mvc;
    @MockitoBean GeminiClient gemini;
    @MockitoBean LectureFileReader files;
    @MockitoBean UserAccountRepository users;
    @MockitoBean AssignmentService assignments;
    @MockitoBean CustomUserDetailsService userDetails;
    @MockitoBean TwoFactorService twoFactor;
    private MockMultipartFile request(String mode) {
        return request(mode, true);
    }
    private MockMultipartFile request(String mode, boolean consent) {
        String payload = "{\"mode\":\""+mode+"\",\"message\":\"Explain collections\",\"language\":\"ru\","
            + "\"history\":[],\"fileConsent\":"+consent+"}";
        return new MockMultipartFile("request","","application/json",payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @BeforeEach void setup() {
        var user = new UserAccount(); user.setId(ACCOUNT_IDS.incrementAndGet()); user.setFullName("Student"); user.setEmail("student@example.com");
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user)); when(assignments.findAll()).thenReturn(List.of());
        when(gemini.configured()).thenReturn(true); when(files.readAll(any())).thenReturn(List.of());
        when(gemini.answer(any(),any())).thenReturn(new AssistantAnswer("A lecture summary",List.of()));
    }
    @Test void requiresLoginAndCsrfForUploads() throws Exception {
        mvc.perform(get("/api/assistant/status")).andExpect(status().is3xxRedirection());
        mvc.perform(multipart("/api/assistant/messages").file(request("summary")).with(user("student@example.com"))).andExpect(status().isForbidden());
        verify(gemini,never()).answer(any(),any());
    }
    @Test void studyModesAcceptAuthenticatedRequests() throws Exception {
        for(String mode:List.of("summary","flashcards","quiz","exam","coach")) {
            mvc.perform(multipart("/api/assistant/messages").file(request(mode)).with(user("student@example.com")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("A lecture summary"));
        }
        verify(gemini,times(5)).answer(any(),any());
    }
    @Test void validatesModeBeforeAnyProviderRequest() throws Exception {
        mvc.perform(multipart("/api/assistant/messages").file(request("system")).with(user("student@example.com")).with(csrf()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION"));
        verify(gemini,never()).answer(any(),any());
    }
    @Test void missingKeyIsExplicitAndFilesAreNotProcessedOrSent() throws Exception {
        when(gemini.configured()).thenReturn(false);
        mvc.perform(get("/api/assistant/status").with(user("student@example.com"))).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false));
        mvc.perform(multipart("/api/assistant/messages").file(request("summary")).with(user("student@example.com")).with(csrf()))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("NOT_CONFIGURED"));
        verify(files,never()).readAll(any()); verify(gemini,never()).answer(any(),any());
    }
    @Test void returnsAnswersWithoutSavingGeneratedTasksAutomatically() throws Exception {
        mvc.perform(multipart("/api/assistant/messages").file(request("summary")).file(new MockMultipartFile("files","lecture.txt","text/plain","Collections lecture".getBytes())).with(user("student@example.com")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("A lecture summary")).andExpect(header().string("Cache-Control","no-store"));
        verify(gemini).answer(any(),any()); verify(assignments,never()).save(any(),anyLong());
    }
    @Test void requiresExplicitConsentBeforeReadingOrSendingLecture() throws Exception {
        var lecture = new MockMultipartFile("files","lecture.txt","text/plain",new byte[]{65});
        mvc.perform(multipart("/api/assistant/messages").file(request("summary", false)).file(lecture)
                .with(user("student@example.com")).with(csrf()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("FILE_CONSENT_REQUIRED"));
        verify(files,never()).readAll(any());
        verify(gemini,never()).answer(any(),any());
        mvc.perform(multipart("/api/assistant/messages").file(request("summary", false))
                .with(user("student@example.com")).with(csrf()))
            .andExpect(status().isOk());
    }

    @Test void rejectsInvalidFilesAndDoesNotCallProvider() throws Exception {
        when(files.readAll(any())).thenThrow(new AssistantException(HttpStatus.BAD_REQUEST,"FILE_FORMAT"));
        mvc.perform(multipart("/api/assistant/messages").file(request("summary")).file(new MockMultipartFile("files","lecture.exe","application/octet-stream",new byte[]{1})).with(user("student@example.com")).with(csrf()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("FILE_FORMAT"));
        verify(gemini,never()).answer(any(),any());
    }

    @Test void uploadedLectureIsNotReusedForAnotherAccount() throws Exception {
        var other = new UserAccount(); other.setId(ACCOUNT_IDS.incrementAndGet()); other.setEmail("other@example.com");
        when(users.findByEmailIgnoreCase(other.getEmail())).thenReturn(Optional.of(other));
        var upload = new MockMultipartFile("files","private.txt","text/plain","PRIVATE_LECTURE".getBytes());
        when(files.readAll(any())).thenAnswer(call -> {
            List<org.springframework.web.multipart.MultipartFile> supplied = call.getArgument(0);
            return supplied == null || supplied.isEmpty() ? List.of()
                : List.of(new LectureFileReader.Lecture("private.txt","text/plain",null,"PRIVATE_LECTURE"));
        });
        mvc.perform(multipart("/api/assistant/messages").file(request("summary")).file(upload)
            .with(user("student@example.com")).with(csrf())).andExpect(status().isOk());
        mvc.perform(multipart("/api/assistant/messages").file(request("summary"))
            .with(user(other.getEmail())).with(csrf())).andExpect(status().isOk());
        @SuppressWarnings({"unchecked","rawtypes"})
        org.mockito.ArgumentCaptor<List<LectureFileReader.Lecture>> sent = org.mockito.ArgumentCaptor.forClass((Class)List.class);
        verify(gemini,times(2)).answer(any(),sent.capture());
        org.junit.jupiter.api.Assertions.assertEquals("PRIVATE_LECTURE",sent.getAllValues().get(0).getFirst().text());
        org.junit.jupiter.api.Assertions.assertTrue(sent.getAllValues().get(1).isEmpty());
    }
}
