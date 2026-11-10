package kz.studhub.news;

import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class NewsForm {
    @NotBlank @Size(max=160) private String title;
    @NotBlank @Size(max=12000) private String body;
    @NotNull private NewsPost.Category category = NewsPost.Category.UPDATE;
    @Size(max=2000) private String link = "";
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate eventDate;
    @AssertTrue(message="Укажите адрес https://… или ссылку на курс /courses/123")
    public boolean isSafeLink() {
        if (link == null || link.isBlank()) return true;
        if (link.matches("/courses/[0-9]+")) return true;
        try {
            URI uri = URI.create(link);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) { return false; }
    }
    public String getTitle() { return title; } public void setTitle(String value) { title=value; }
    public String getBody() { return body; } public void setBody(String value) { body=value; }
    public NewsPost.Category getCategory() { return category; } public void setCategory(NewsPost.Category value) { category=value; }
    public String getLink() { return link; } public void setLink(String value) { link=value == null ? "" : value.trim(); }
    public LocalDate getEventDate() { return eventDate; } public void setEventDate(LocalDate value) { eventDate=value; }
    public static NewsForm from(NewsPost post) {
        NewsForm form=new NewsForm(); form.title=post.getTitle(); form.body=post.getBody();
        form.category=post.getCategory(); form.link=post.getLink(); form.eventDate=post.getEventDate(); return form;
    }
}
