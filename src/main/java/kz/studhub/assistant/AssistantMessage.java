package kz.studhub.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record AssistantMessage(
        @NotNull @Pattern(regexp="summary|explain|tasks|flashcards|quiz|exam|coach") String mode,
        @NotNull @Size(max=6000) String message,
        @NotNull @Pattern(regexp="ru|en|kk") String language,
        @NotNull @Size(max=8) List<@NotNull @Valid Turn> history,
        boolean fileConsent) {
    public AssistantMessage(String mode, String message, String language, List<Turn> history) {
        this(mode, message, language, history, false);
    }

    public record Turn(@NotNull @Pattern(regexp="user|assistant") String role,
                       @NotBlank @Size(max=6000) String text) {}
}
