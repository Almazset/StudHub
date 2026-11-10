package kz.studhub.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class SupportRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long ownerId;
    @Column(nullable = false, length = 120) private String subject;
    @Column(nullable = false, length = 2000) private String message;
    private Instant createdAt = Instant.now();
    private boolean closed;
    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long value) { ownerId = value; }
    public String getSubject() { return subject; }
    public void setSubject(String value) { subject = value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { message = value; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isClosed() { return closed; }
    public void setClosed(boolean value) { closed = value; }
}
