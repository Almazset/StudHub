package kz.studhub.news;

import jakarta.persistence.*;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "news_posts")
public class NewsPost {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(nullable = false, length = 12000)
    private String body;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Category category;
    @Column(length = 2000)
    private String link;
    private LocalDate eventDate;
    private LocalDateTime publishedAt;
    // A missing flag is a publication created before drafts were introduced.
    private Boolean published = false;
    @Column(length = 120)
    private String sourceName;
    private LocalDate sourceDate;
    @Enumerated(EnumType.STRING) @Column(length = 16)
    private Status status;

    public enum Category {
        COURSE("Курсы"), UPDATE("Обновления StudHub"), ANNOUNCEMENT("Анонсы"), USEFUL("Полезное");
        private final String label;
        Category(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum Status {
        PLANNED("Планируется"), IN_PROGRESS("В разработке"), RELEASED("Выпущено");
        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Category getCategory() { return category; }
    public String getLink() { return link; }
    public LocalDate getEventDate() { return eventDate; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public boolean isPublished() { return !Boolean.FALSE.equals(published); }
    public String getSourceName() { return sourceName; }
    public LocalDate getSourceDate() { return sourceDate; }
    public Status getStatus() { return status; }
    public boolean isExternal() { return externalHost(link) != null; }
    public String getSourceLabel() { return sourceName == null || sourceName.isBlank() ? externalHost(link) : sourceName; }
    public String getSafeLink() { return link != null && !link.isBlank() && isAllowedLink(link) ? link : null; }
    public String getExcerpt() { return body.length() > 180 ? body.substring(0, 180) + "…" : body; }

    void update(NewsForm form) {
        title = form.getTitle().strip();
        body = form.getBody().strip();
        category = form.getCategory();
        link = form.getLink();
        eventDate = form.getEventDate();
        status = category == Category.UPDATE ? form.getStatus() : null;
        sourceName = isExternal() ? form.getSourceName() : null;
        sourceDate = isExternal() ? form.getSourceDate() : null;
    }

    void publish() {
        if (publishedAt == null) publishedAt = LocalDateTime.now();
        published = true;
    }

    void unpublish() {
        published = false;
    }

    static boolean isAllowedLink(String link) {
        return link == null || link.isBlank() || link.matches("/courses/[0-9]+") || externalHost(link) != null;
    }

    static String externalHost(String link) {
        if (link == null || link.isBlank()) return null;
        try {
            URI uri = URI.create(link);
            boolean web = "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
            return web && uri.getUserInfo() == null ? uri.getHost() : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
