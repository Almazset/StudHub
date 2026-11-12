package kz.studhub.chat;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "chat_message_room", columnList = "roomId,id"),
    @Index(name = "chat_message_sender_time", columnList = "senderId,createdAt")
}, uniqueConstraints = @UniqueConstraint(name = "chat_message_retry", columnNames = {"roomId", "senderId", "clientId"}))
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, length = 36) String roomId;
    @Column(nullable = false) Long senderId;
    @Column(nullable = false, length = 36) String clientId;
    @Column(nullable = false, length = 4000) String text;
    @Column(nullable = false) Instant createdAt;
    Instant editedAt;
    protected ChatMessage() {}
    ChatMessage(String roomId, Long senderId, String text, String clientId) {
        this.roomId = roomId; this.senderId = senderId; this.text = text; this.clientId = clientId; createdAt = Instant.now();
    }
}
