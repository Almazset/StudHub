package kz.studhub.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantNotesService {
    private static final int NOTE_LIMIT = 100;
    private final AssistantNoteRepository notes;
    private final UserAccountRepository users;

    public record NoteInput(
        @NotBlank @Size(max = AssistantNote.TITLE_LENGTH) String title,
        @NotBlank @Size(max = AssistantNote.BODY_LENGTH) String body,
        @NotNull @Size(max = AssistantNote.TERM_COUNT) List<@NotNull @Valid Term> terms
    ) {}

    public record Term(
        @NotBlank @Size(max = AssistantNote.TERM_LENGTH) String term,
        @NotBlank @Size(max = AssistantNote.DEFINITION_LENGTH) String definition
    ) {}

    public record NoteSummary(Long id, String title, Instant createdAt) {}
    public record NoteDetail(Long id, String title, String body, List<Term> terms, Instant createdAt) {}

    AssistantNotesService(AssistantNoteRepository notes, UserAccountRepository users) {
        this.notes = notes;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<NoteSummary> list(String email) {
        Long ownerId = account(email).getId();
        return notes.findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId).stream()
            .map(note -> new NoteSummary(note.getId(), note.getTitle(), note.getCreatedAt()))
            .toList();
    }

    @Transactional(readOnly = true)
    public NoteDetail read(String email, long id) {
        return detail(ownedNote(id, account(email).getId()));
    }

    @Transactional
    public NoteDetail create(String email, NoteInput input) {
        String title = clean(input.title());
        String body = clean(input.body());
        var terms = input.terms().stream()
            .map(term -> new AssistantNote.GlossaryTerm(clean(term.term()), clean(term.definition())))
            .toList();

        // Serialize quota checks for simultaneous saves from the same account.
        UserAccount owner = users.findLockedByEmail(email).orElseThrow(AssistantNotesService::unauthorized);
        if (notes.countByOwnerId(owner.getId()) >= NOTE_LIMIT) {
            throw new AssistantException(HttpStatus.CONFLICT, "NOTE_LIMIT");
        }

        return detail(notes.save(new AssistantNote(owner, title, body, terms, Instant.now())));
    }

    @Transactional
    public void delete(String email, long id) {
        notes.delete(ownedNote(id, account(email).getId()));
    }

    private AssistantNote ownedNote(long id, long ownerId) {
        return notes.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(() -> new AssistantException(HttpStatus.NOT_FOUND, "NOTE_NOT_FOUND"));
    }

    private UserAccount account(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(AssistantNotesService::unauthorized);
    }

    private static AssistantException unauthorized() {
        return new AssistantException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    private static String clean(String value) {
        if (value.isBlank() || value.indexOf(0) >= 0) {
            throw new AssistantException(HttpStatus.BAD_REQUEST, "VALIDATION");
        }
        return value.strip();
    }

    private static NoteDetail detail(AssistantNote note) {
        var terms = note.terms.stream().map(term -> new Term(term.term, term.definition)).toList();
        return new NoteDetail(note.id, note.title, note.body, terms, note.createdAt);
    }
}
