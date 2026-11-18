package kz.studhub.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import static org.hamcrest.Matchers.containsString;
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
        client = new GeminiClient(builder.build(), json, "unit-test-key", "gemini-3.8-flash");
    }

    private AssistantMessage request(String mode) {
        return new AssistantMessage(mode, "Explain the lecture", "ru", List.of(
            new AssistantMessage.Turn("user", "Question"),
            new AssistantMessage.Turn("assistant", "Previous answer")));
    }

    private String response(String text, String finish) throws Exception {
        return json.writeValueAsString(Map.of("candidates", List.of(Map.of(
            "finishReason", finish, "content", Map.of("parts", List.of(Map.of("text", text)))))));
    }

    @Test void sendsMultipleLectureFormatsToTheVerifiedEndpoint() throws Exception {
        var batches = sampleBatches();
        String payload = """
            {"text":"Summary ready","note":{"title":"Transit concepts","body":"Routes and stations","terms":[{"term":"Route","definition":"A path joining locations."}]}}
            """;
        var reader = new LectureFileReader();
        var mixed = reader.readAll(batches.get(0));
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
            .andExpect(method(HttpMethod.POST)).andExpect(header("x-goog-api-key", "unit-test-key"))
            .andExpect(jsonPath("$.store").value(false)).andExpect(jsonPath("$.contents[1].role").value("model"))
            .andExpect(jsonPath("$.generationConfig.responseFormat.text.mimeType").value("APPLICATION_JSON"))
            .andExpect(jsonPath("$.generationConfig.responseFormat.text.schema.properties.note.properties.terms.maxItems").value(20))
            .andExpect(jsonPath("$.contents[2].parts[0].text", containsString("Attached lecture files: 3")))
            .andExpect(jsonPath("$.contents[2].parts[1].text", containsString("routes.pdf")))
            .andExpect(jsonPath("$.contents[2].parts[2].inlineData.mimeType").value("application/pdf"))
            .andExpect(jsonPath("$.contents[2].parts[2].inlineData.data").value(Base64.getEncoder().encodeToString(mixed.getFirst().bytes())))
            .andExpect(jsonPath("$.contents[2].parts[4].inlineData.mimeType").value("image/png"))
            .andExpect(jsonPath("$.contents[2].parts[6].text", containsString("5371")))
            .andRespond(withSuccess(response(payload, "STOP"), MediaType.APPLICATION_JSON));
        var extracted = reader.readAll(batches.get(1));
        server.expect(anything())
            .andExpect(jsonPath("$.contents[2].parts[2].text", containsString("7416")))
            .andExpect(jsonPath("$.contents[2].parts[4].text", containsString("6824")))
            .andExpect(jsonPath("$.contents[2].parts[6].text", containsString("9531")))
            .andRespond(withSuccess(response(payload, "STOP"), MediaType.APPLICATION_JSON));
        var jpeg = reader.readAll(batches.get(2));
        server.expect(anything())
            .andExpect(jsonPath("$.contents[2].parts[2].inlineData.mimeType").value("image/jpeg"))
            .andRespond(withSuccess(response(payload, "STOP"), MediaType.APPLICATION_JSON));

        var answer = client.answer(request("summary"), mixed);
        assertEquals("Transit concepts", answer.note().title());
        assertEquals("Route", answer.note().terms().getFirst().term());
        assertTrue(answer.tasks().isEmpty());
        client.answer(request("summary"), extracted);
        client.answer(request("summary"), jpeg);
        server.verify();
    }

    @Test void usesTheQuizSchemaAndKeepsQuestionsFromOtherModesOut() throws Exception {
        String payload = """
            {"text":"Try this quiz","quiz":[{"question":"Which tag belongs to the Birch route?",
            "options":["4182","2649","5371","7416"],"correctIndex":1,"explanation":"The Birch diagram names tag 2649."}]}
            """;
        server.expect(anything())
            .andExpect(jsonPath("$.generationConfig.responseFormat.text.schema.properties.quiz.items.properties.correctIndex.minimum").value(0))
            .andExpect(jsonPath("$.generationConfig.responseFormat.text.schema.properties.tasks").doesNotExist())
            .andExpect(jsonPath("$.systemInstruction.parts[0].text", containsString("grounded in the supplied lecture text or files")))
            .andExpect(jsonPath("$.systemInstruction.parts[0].text", containsString("Put answers and explanations only in the quiz items")))
            .andRespond(withSuccess(response(payload, "STOP"), MediaType.APPLICATION_JSON));
        var answer = client.answer(request("quiz"), List.of());
        assertEquals(1, answer.quiz().getFirst().correctIndex());
        assertEquals("2649", answer.quiz().getFirst().options().get(1));
        assertTrue(answer.flashcards().isEmpty());
        assertNull(answer.note());
        server.verify();
    }

    @Test void rejectsAmbiguousQuizOptionsAndInvalidCorrectIndices() throws Exception {
        String valid = """
            {"text":"Try this quiz","quiz":[{"question":"Which one?","options":["One","Two","Three","Four"],"correctIndex":0,"explanation":"One."}]}
            """;
        assertInvalid(valid.replace("\"Four\"", "\" one \""), "quiz");
        assertInvalid(valid.replace("\"correctIndex\":0", "\"correctIndex\":4"), "quiz");
        assertInvalid(valid.replace("\"correctIndex\":0", "\"correctIndex\":0.5"), "quiz");
    }

    @Test void decodesStudyCardsExamSessionsAndTutorHints() throws Exception {
        assertEquals("A path.", parse("""
            {"text":"Cards ready","flashcards":[{"question":"What is a route?","answer":"A path."}]}
            """, "flashcards").flashcards().getFirst().answer());
        assertEquals(30, parse("""
            {"text":"Plan ready","plan":[{"title":"Day 1","durationMinutes":30,"activities":["Recall the main definitions."]}]}
            """, "exam").plan().getFirst().durationMinutes());
        assertEquals("Write down the definition.", parse("""
            {"text":"Start here","coach":{"hint":"Read the question.","explanation":"It asks for a useful definition.","nextStep":"Write down the definition."}}
            """, "coach").coach().nextStep());
        assertNull(parse("""
            {"text":"Please attach your lecture.","note":null}
            """, "summary").note());
        assertTrue(parse("""
            {"text":"Please attach your lecture.","quiz":[]}
            """, "quiz").quiz().isEmpty());
    }

    @Test void rejectsMalformedOrOversizedGeneratedMaterial() throws Exception {
        String body = json.writeValueAsString(Map.of("text", "Practice", "tasks",
            List.of(Map.of("title", "Explain lists", "description", "Compare two collection types."))));
        var answer = parse(body, "tasks");
        assertEquals(1, answer.tasks().size());
        assertEquals("Explain lists", answer.tasks().getFirst().title());
        assertInvalid(body.replace("Explain lists", "x".repeat(151)), "tasks");
        assertInvalid("<script>bad()</script>", "summary");
        assertInvalid("{\"text\":\"OK\",\"note\":null}{}", "summary");
        assertInvalid("""
            {"text":"OK","note":{"title":"Topic","body":"Main idea","terms":[{"term":"Example","definition":""}]}}
            """, "summary");
    }

    @Test void handlesProviderFailuresWithoutLeakingItsBodyOrApiKey() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.BAD_REQUEST)
            .body("""
                {"error":{"status":"INVALID_ARGUMENT","message":"secret provider diagnostic unit-test-key","details":[{"reason":"API_KEY_INVALID"}]}}
                """));
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        var failure = assertThrows(AssistantException.class, () -> client.answer(request("summary"), List.of()));
        assertEquals("PROVIDER_AUTH", failure.code());
        assertFalse(failure.getMessage().contains("unit-test-key"));
        assertEquals("PROVIDER_MODEL", assertThrows(AssistantException.class, () -> client.answer(request("summary"), List.of())).code());
        assertEquals("PROVIDER_UNAVAILABLE", assertThrows(AssistantException.class, () -> client.answer(request("summary"), List.of())).code());
        server.verify();
    }

    @Test void handlesTimeoutsTruncatedAndBlockedResponses() throws Exception {
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException()));
        assertEquals("TIMEOUT", assertThrows(AssistantException.class, () -> client.answer(request("summary"), List.of())).code());
        assertEquals("TRUNCATED", assertThrows(AssistantException.class, () -> client.parse(json.readTree(response("partial", "MAX_TOKENS")), "summary")).code());
        assertEquals("BLOCKED", assertThrows(AssistantException.class, () -> client.parse(json.readTree(response("", "SAFETY")), "summary")).code());
        JsonNode blockedPrompt = json.readTree("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");
        assertEquals("BLOCKED", assertThrows(AssistantException.class, () -> client.parse(blockedPrompt, "summary")).code());
        server.verify();
    }

    @Test void missingConfigurationDoesNotMakeNetworkRequests() {
        var missing = new GeminiClient(RestClient.create(), json, "", "gemini-3.8-flash");
        assertFalse(missing.configured());
        assertEquals("NOT_CONFIGURED", assertThrows(AssistantException.class, () -> missing.answer(request("summary"), List.of())).code());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "STUDHUB_RUN_GEMINI_LIVE", matches = "1")
    void realGeminiReadsSyntheticLecturesInEverySupportedFormat() throws Exception {
        String key = System.getenv().getOrDefault("GEMINI_API_KEY", "").trim();
        assertFalse(key.isBlank(), "Configure the Gemini API key in the environment to run the live test.");
        String model = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.8-flash");
        var live = new GeminiClient(new GeminiConfig().geminiRestClient(), json, key, model);
        var request = new AssistantMessage("summary",
            "Summarize each sample route and include its exact numeric tag in the note body. Use every attached file.",
            "en", List.of(), true);
        var reader = new LectureFileReader();
        var batches = sampleBatches();
        var expected = List.of(List.of("4182", "2649", "5371"), List.of("7416", "6824", "9531"), List.of("3078"));
        for (int i = 0; i < batches.size(); i++) {
            var result = live.answer(request, reader.readAll(batches.get(i)));
            assertNotNull(result.note(), "Gemini did not return a structured note.");
            for (String tag : expected.get(i))
                assertTrue(result.note().body().contains(tag), "Missing a known fact from a synthetic lecture.");
        }
    }

    private AssistantAnswer parse(String body, String mode) throws Exception {
        return client.parse(json.readTree(response(body, "STOP")), mode);
    }

    private void assertInvalid(String body, String mode) {
        assertEquals("INVALID_RESPONSE", assertThrows(AssistantException.class, () -> parse(body, mode)).code());
    }

    private static List<List<MultipartFile>> sampleBatches() throws Exception {
        return List.of(
            List.of(file("routes.pdf", pdf("The Alder route has tag 4182.")),
                file("diagram.png", image("png", "The Birch route has tag 2649.")),
                file("exercise.docx", office("word/document.xml",
                    "<w:p xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:r><w:t>The Cedar route has tag 5371.</w:t></w:r></w:p>"))),
            List.of(file("lecture.txt", "The Elm route has tag 7416.".getBytes(StandardCharsets.UTF_8)),
                file("notes.md", "## The Fir route\nThe Fir route has tag 6824.".getBytes(StandardCharsets.UTF_8)),
                file("slides.pptx", office("ppt/slides/slide1.xml",
                    "<a:p xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'><a:r><a:t>The Holly route has tag 9531.</a:t></a:r></a:p>"))),
            List.of(file("chart.jpg", image("jpeg", "The Maple route has tag 3078."))));
    }

    private static MockMultipartFile file(String name, byte[] contents) {
        return new MockMultipartFile("files", name, "application/octet-stream", contents);
    }

    private static byte[] pdf(String lecture) throws Exception {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            try (var stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 18);
                stream.newLineAtOffset(72, 700);
                stream.showText(lecture);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] image(String format, String lecture) throws Exception {
        var image = new BufferedImage(750, 130, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.setFont(new Font("SansSerif", Font.BOLD, 28));
            graphics.drawString(lecture, 20, 65);
        } finally {
            graphics.dispose();
        }
        try (var output = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(image, format, output));
            return output.toByteArray();
        }
    }

    private static byte[] office(String entry, String text) throws Exception {
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(text.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return output.toByteArray();
        }
    }
}
