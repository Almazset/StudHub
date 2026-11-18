package kz.studhub.assistant;

import java.util.List;

public record AssistantAnswer(String text, List<Task> tasks, Note note,
                              List<Flashcard> flashcards, List<QuizQuestion> quiz,
                              List<PlanSession> plan, Coach coach) {
    public AssistantAnswer(String text, List<Task> tasks) {
        this(text, tasks, null, List.of(), List.of(), List.of(), null);
    }

    public record Task(String title, String description) {}
    public record Term(String term, String definition) {}
    public record Note(String title, String body, List<Term> terms) {}
    public record Flashcard(String question, String answer) {}
    public record QuizQuestion(String question, List<String> options, int correctIndex, String explanation) {}
    public record PlanSession(String title, int durationMinutes, List<String> activities) {}
    public record Coach(String hint, String explanation, String nextStep) {}
}
