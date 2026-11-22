package kz.studhub.news;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.Errors;

public class NewsForm {
    @NotBlank @Size(max = 160)
    private String title;
    @NotBlank @Size(max = 12000)
    private String body;
    @NotNull
    private NewsPost.Category category = NewsPost.Category.UPDATE;
    @Size(max = 2000)
    private String link = "";
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate eventDate;
    @Size(max = 120)
    private String sourceName;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate sourceDate;
    private NewsPost.Status status;

    @AssertTrue(message = "Укажите веб-адрес http://… или https://… либо ссылку на курс /courses/123")
    public boolean isSafeLink() {
        return NewsPost.isAllowedLink(link);
    }

    void validateDetails(Errors errors) {
        if (category == NewsPost.Category.UPDATE && status == null && !errors.hasFieldErrors("status")) {
            errors.rejectValue("status", "news.status.required", "Выберите статус обновления");
        }
        if (NewsPost.externalHost(link) != null) {
            if (sourceName == null || sourceName.isBlank()) {
                errors.rejectValue("sourceName", "news.sourceName.required", "Укажите источник внешней новости");
            }
            if (sourceDate == null && !errors.hasFieldErrors("sourceDate")) {
                errors.rejectValue("sourceDate", "news.sourceDate.required", "Укажите дату публикации у источника");
            }
        }
    }

    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getBody() { return body; }
    public void setBody(String value) { body = value; }
    public NewsPost.Category getCategory() { return category; }
    public void setCategory(NewsPost.Category value) { category = value; }
    public String getLink() { return link; }
    public void setLink(String value) { link = value == null ? "" : value.strip(); }
    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate value) { eventDate = value; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String value) { sourceName = value == null ? null : value.strip(); }
    public LocalDate getSourceDate() { return sourceDate; }
    public void setSourceDate(LocalDate value) { sourceDate = value; }
    public NewsPost.Status getStatus() { return status; }
    public void setStatus(NewsPost.Status value) { status = value; }

    static NewsForm from(NewsPost post) {
        NewsForm form = new NewsForm();
        form.title = post.getTitle();
        form.body = post.getBody();
        form.category = post.getCategory();
        form.link = post.getLink();
        form.eventDate = post.getEventDate();
        form.sourceName = post.getSourceName();
        form.sourceDate = post.getSourceDate();
        form.status = post.getStatus();
        return form;
    }
}
