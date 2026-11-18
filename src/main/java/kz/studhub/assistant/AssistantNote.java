package kz.studhub.assistant;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import kz.studhub.model.UserAccount;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "assistant_notes",
    indexes = @Index(name = "assistant_notes_owner_created", columnList = "owner_id, created_at, id"))
public class AssistantNote {
    static final int TITLE_LENGTH = 150;
    static final int BODY_LENGTH = 18000;
    static final int TERM_COUNT = 20;
    static final int TERM_LENGTH = 100;
    static final int DEFINITION_LENGTH = 500;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserAccount owner;

    @Column(nullable = false, length = TITLE_LENGTH)
    String title;

    @Column(nullable = false, length = BODY_LENGTH)
    String body;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "assistant_note_terms", joinColumns = @JoinColumn(name = "note_id"))
    @OrderColumn(name = "term_order", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    List<GlossaryTerm> terms = new ArrayList<>();

    protected AssistantNote() {}

    AssistantNote(UserAccount owner, String title, String body, List<GlossaryTerm> terms, Instant createdAt) {
        this.owner = owner;
        this.title = title;
        this.body = body;
        this.terms.addAll(terms);
        this.createdAt = createdAt;
    }

    @Embeddable
    public static class GlossaryTerm {
        @Column(nullable = false, length = TERM_LENGTH)
        String term;

        @Column(nullable = false, length = DEFINITION_LENGTH)
        String definition;

        protected GlossaryTerm() {}

        GlossaryTerm(String term, String definition) {
            this.term = term;
            this.definition = definition;
        }
    }
}
