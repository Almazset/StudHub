package kz.studhub.news;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "news_posts")
public class NewsPost {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 12000) private String body;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Category category;
    @Column(length = 2000) private String link;
    private LocalDate eventDate;
    @Column(nullable = false) private LocalDateTime publishedAt;
    public enum Category {
        COURSE("Курсы"), UPDATE("Обновления сайта"), ANNOUNCEMENT("Анонсы"), USEFUL("Полезное");
        private final String label;
        Category(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Category getCategory() { return category; }
    public String getLink() { return link; }
    public LocalDate getEventDate() { return eventDate; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public String getExcerpt() { return body.length() > 180 ? body.substring(0,180) + "…" : body; }
    public void update(NewsForm form) {
        title = form.getTitle().trim(); body = form.getBody().trim(); category = form.getCategory();
        link = form.getLink(); eventDate = form.getEventDate();
        if (publishedAt == null) publishedAt = LocalDateTime.now();
    }
}
