package kz.studhub.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpStatus;

import static kz.studhub.assistant.AssistantAnswer.*;

final class GeminiAnswerFormat {
    private static final Map<String, Object> STRING = Map.of("type", "string");
    private static final int MAX_TERMS = 20;
    private static final int MAX_TASKS = 5;
    private static final int MAX_FLASHCARDS = 10;
    private static final int MAX_QUESTIONS = 8;
    private static final int MAX_SESSIONS = 10;
    private static final int MAX_ACTIVITIES = 6;

    private GeminiAnswerFormat() {}

    static Map<String, Object> schema(String mode) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("text", STRING);
        switch (mode) {
            case "tasks" -> properties.put("tasks", array(MAX_TASKS,
                object(false, Map.of("title", STRING, "description", STRING))));
            case "summary" -> properties.put("note", object(true, Map.of(
                "title", STRING, "body", STRING,
                "terms", array(MAX_TERMS, object(false, Map.of("term", STRING, "definition", STRING))))));
            case "flashcards" -> properties.put("flashcards", array(MAX_FLASHCARDS,
                object(false, Map.of("question", STRING, "answer", STRING))));
            case "quiz" -> properties.put("quiz", array(MAX_QUESTIONS, object(false, Map.of(
                "question", STRING, "options", Map.of("type", "array", "items", STRING, "minItems", 4, "maxItems", 4),
                "correctIndex", integer(0, 3), "explanation", STRING))));
            case "exam" -> properties.put("plan", array(MAX_SESSIONS, object(false, Map.of(
                "title", STRING, "durationMinutes", integer(5, 240), "activities", array(MAX_ACTIVITIES, STRING)))));
            case "coach" -> properties.put("coach", object(true,
                Map.of("hint", STRING, "explanation", STRING, "nextStep", STRING)));
            default -> { /* Explain needs only text. */ }
        }
        return object(false, properties);
    }

    static AssistantAnswer read(JsonNode result, String mode) {
        if (result == null || !result.isObject()) throw invalid();
        String text = text(result, "text", 20_000);
        return switch (mode) {
            case "summary" -> new AssistantAnswer(text, List.of(), note(result.get("note")), List.of(), List.of(), List.of(), null);
            case "flashcards" -> new AssistantAnswer(text, List.of(), null,
                list(result.get("flashcards"), 0, MAX_FLASHCARDS, GeminiAnswerFormat::flashcard), List.of(), List.of(), null);
            case "quiz" -> new AssistantAnswer(text, List.of(), null, List.of(),
                list(result.get("quiz"), 0, MAX_QUESTIONS, GeminiAnswerFormat::question), List.of(), null);
            case "exam" -> new AssistantAnswer(text, List.of(), null, List.of(), List.of(),
                list(result.get("plan"), 0, MAX_SESSIONS, GeminiAnswerFormat::session), null);
            case "coach" -> new AssistantAnswer(text, List.of(), null, List.of(), List.of(), List.of(), coach(result.get("coach")));
            case "tasks" -> new AssistantAnswer(text, list(result.get("tasks"), 0, MAX_TASKS, GeminiAnswerFormat::task));
            default -> new AssistantAnswer(text, List.of());
        };
    }

    private static Note note(JsonNode node) {
        if (node != null && node.isNull()) return null;
        if (node == null || !node.isObject()) throw invalid();
        return new Note(text(node, "title", 150), text(node, "body", 18_000),
            list(node.get("terms"), 0, MAX_TERMS, term -> new Term(text(term, "term", 100), text(term, "definition", 500))));
    }

    private static Flashcard flashcard(JsonNode node) {
        return new Flashcard(text(node, "question", 500), text(node, "answer", 1000));
    }

    private static QuizQuestion question(JsonNode node) {
        String question = text(node, "question", 500);
        List<String> options = list(node.get("options"), 4, 4, value -> text(value, 350));
        if (options.stream().map(value -> value.toLowerCase(Locale.ROOT)).distinct().count() != options.size()) throw invalid();
        return new QuizQuestion(question, options, integer(node.get("correctIndex"), 0, options.size() - 1),
            text(node, "explanation", 1000));
    }

    private static PlanSession session(JsonNode node) {
        return new PlanSession(text(node, "title", 150), integer(node.get("durationMinutes"), 5, 240),
            list(node.get("activities"), 1, MAX_ACTIVITIES, value -> text(value, 500)));
    }

    private static Coach coach(JsonNode node) {
        if (node != null && node.isNull()) return null;
        if (node == null || !node.isObject()) throw invalid();
        return new Coach(text(node, "hint", 1000), text(node, "explanation", 1500), text(node, "nextStep", 1000));
    }

    private static Task task(JsonNode node) {
        String title = text(node, "title", 150);
        if (title.length() < 2) throw invalid();
        return new Task(title, text(node, "description", 1400));
    }

    private static String text(JsonNode node, String field, int max) {
        return text(node.path(field), max);
    }

    private static String text(JsonNode node, int max) {
        if (node == null || !node.isTextual()) throw invalid();
        String value = node.textValue().strip();
        if (value.isBlank() || value.length() > max || value.indexOf('\0') >= 0) throw invalid();
        return value;
    }

    private static int integer(JsonNode node, int min, int max) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) throw invalid();
        int value = node.intValue();
        if (value < min || value > max) throw invalid();
        return value;
    }

    private static <T> List<T> list(JsonNode node, int min, int max, Function<JsonNode, T> readItem) {
        if (node == null || !node.isArray() || node.size() < min || node.size() > max) throw invalid();
        List<T> values = new ArrayList<>();
        node.forEach(item -> values.add(readItem.apply(item)));
        return List.copyOf(values);
    }

    private static Map<String, Object> object(boolean nullable, Map<String, Object> properties) {
        return Map.of("type", nullable ? List.of("object", "null") : "object",
            "properties", properties, "required", List.copyOf(properties.keySet()));
    }

    private static Map<String, Object> array(int max, Map<String, Object> items) {
        return Map.of("type", "array", "maxItems", max, "items", items);
    }

    private static Map<String, Object> integer(int min, int max) {
        return Map.of("type", "integer", "minimum", min, "maximum", max);
    }

    static AssistantException invalid() {
        return new AssistantException(HttpStatus.BAD_GATEWAY, "INVALID_RESPONSE");
    }
}
