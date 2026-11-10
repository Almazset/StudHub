package kz.studhub.assistant;

import java.util.List;

public record AssistantAnswer(String text, List<Task> tasks) {
    public record Task(String title, String description) {}
}
