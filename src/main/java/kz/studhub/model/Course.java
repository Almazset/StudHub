package kz.studhub.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "courses")
public class Course {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank @Size(min = 2, max = 120)
    @Column(nullable = false, length = 120)
    private String title;

    @Size(max = 1000)
    @Column(length = 1000)
    private String description;

    @NotBlank @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String teacherName;

    @Size(max = 2000)
    @jakarta.validation.constraints.Pattern(regexp = "^$|https://[A-Za-z0-9.-]+(?:/[^\\s]*)?", message = "Укажите HTTPS-ссылку на платформу курса")
    @Column(length = 2000)
    private String externalUrl;

    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String value) { externalUrl = value == null ? null : value.trim(); }

    public Course() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }
}
