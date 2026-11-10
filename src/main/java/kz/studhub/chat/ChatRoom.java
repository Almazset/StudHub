package kz.studhub.chat;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_rooms")
public class ChatRoom {
    @Id @Column(length = 36) String id;
    @Column(nullable = false, length = 10) String kind;
    @Column(nullable = false, length = 80) String name;
    @Column(unique = true, length = 50) String directKey;
    Long ownerId;
    @Column(nullable = false) Instant updatedAt;
    protected ChatRoom() {}
    ChatRoom(String id, String kind, String name, Long ownerId) {
        this.id = id; this.kind = kind; this.name = name; this.ownerId = ownerId; updatedAt = Instant.now();
    }
}
