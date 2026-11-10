package kz.studhub.assistant;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private MockRestServiceServer server;
    private GeminiClient client;
    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com/v1beta/");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiClient(builder.build(),json,"unit-test-key","gemini-3.8-flash");
    }
    private AssistantMessage request(String mode) { return new AssistantMessage(mode,"Explain the lecture","ru",List.of(new AssistantMessage.Turn("user","Question"),new AssistantMessage.Turn("assistant","Previous answer"))); }
    private String response(String text,String finish) throws Exception {
        return json.writeValueAsString(Map.of("candidates",List.of(Map.of("finishReason",finish,"content",Map.of("parts",List.of(Map.of("text",text)))))));
    }
    @Test void sendsFilesAndHistoryToFixedEndpointWithoutPuttingKeyInUrl() throws Exception {
        String payload = json.writeValueAsString(Map.of("text","A useful summary","tasks",List.of()));
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
            .andExpect(method(HttpMethod.POST)).andExpect(header("x-goog-api-key","unit-test-key"))
            .andExpect(jsonPath("$.store").value(false)).andExpect(jsonPath("$.contents[1].role").value("model"))
            .andExpect(jsonPath("$.generationConfig.responseFormat.text.mimeType").value("APPLICATION_JSON"))
            .andExpect(jsonPath("$.contents[2].parts[2].inlineData.mimeType").value("application/pdf"))
            .andRespond(withSuccess(response(payload,"STOP"),MediaType.APPLICATION_JSON));
        var answer = client.answer(request("summary"),List.of(new LectureFileReader.Lecture("lecture.pdf","application/pdf",new byte[]{1,2,3},null)));
        assertEquals("A useful summary",answer.text()); assertTrue(answer.tasks().isEmpty()); server.verify();
    }
    @Test void validatesGeneratedTaskShapesAndDoesNotTreatHtmlAsExecutableActions() throws Exception {
        String body = json.writeValueAsString(Map.of("text","Practice","tasks",List.of(Map.of("title","Explain lists","description","Compare two collection types."))));
        var answer = client.parse(json.readTree(response(body,"STOP")),"tasks");
        assertEquals(1,answer.tasks().size()); assertEquals("Explain lists",answer.tasks().getFirst().title());
        String invalid = json.writeValueAsString(Map.of("text","Practice","tasks",List.of(Map.of("title","x".repeat(151),"description","Bad"))));
        assertEquals("INVALID_RESPONSE",assertThrows(AssistantException.class,() -> client.parse(json.readTree(response(invalid,"STOP")),"tasks")).code());
        assertEquals("INVALID_RESPONSE",assertThrows(AssistantException.class,() -> client.parse(json.readTree(response("<script>bad()</script>","STOP")),"summary")).code());
    }
    @Test void handlesProviderFailuresWithoutLeakingItsBodyOrApiKey() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN).body("secret provider diagnostic unit-test-key"));
        var failure = assertThrows(AssistantException.class,() -> client.answer(request("summary"),List.of()));
        assertEquals("PROVIDER_AUTH",failure.code()); assertFalse(failure.getMessage().contains("unit-test-key")); server.verify();
    }
    @Test void handlesTimeoutsTruncatedAndBlockedResponses() throws Exception {
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException()));
        assertEquals("TIMEOUT",assertThrows(AssistantException.class,() -> client.answer(request("summary"),List.of())).code());
        assertEquals("TRUNCATED",assertThrows(AssistantException.class,() -> client.parse(json.readTree(response("partial","MAX_TOKENS")),"summary")).code());
        assertEquals("BLOCKED",assertThrows(AssistantException.class,() -> client.parse(json.readTree(response("","SAFETY")),"summary")).code());
        server.verify();
    }
    @Test void missingConfigurationDoesNotMakeNetworkRequests() {
        var missing = new GeminiClient(RestClient.create(),json,"","gemini-3.8-flash");
        assertFalse(missing.configured());
        assertEquals("NOT_CONFIGURED",assertThrows(AssistantException.class,() -> missing.answer(request("summary"),List.of())).code());
    }
}
