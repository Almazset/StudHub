package kz.studhub.web;

import kz.studhub.chat.*;
import kz.studhub.config.SecurityConfig;
import kz.studhub.repository.UserAccountRepository;
import kz.studhub.security.TwoFactorService;
import kz.studhub.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatApiController.class)
@Import(SecurityConfig.class)
class ChatApiSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ChatService chat;
    @MockitoBean UserAccountRepository users;
    @MockitoBean AssignmentService assignments;
    @MockitoBean CustomUserDetailsService userDetails;
    @MockitoBean TwoFactorService twoFactor;
    @Test void requiresSessionAndCsrf() throws Exception {
        mvc.perform(get("/api/chat/rooms")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/chat/direct").with(user("alice")).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":2}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(chat);
    }
    @Test void validatesPayloadAndUsesAuthenticatedIdentity() throws Exception {
        mvc.perform(post("/api/chat/rooms/global/messages").with(user("alice")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\" \",\"clientId\":\"b55aad2a-a338-4561-9204-52adf076b191\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat/groups").with(user("alice")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Study\",\"userIds\":[]}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(chat);
        mvc.perform(post("/api/chat/direct").with(user("alice")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":2,\"senderId\":999}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(chat).direct("alice",2L);
    }
}
