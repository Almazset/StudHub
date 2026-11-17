package kz.studhub.chat;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.BatchSize;

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
    Long replyToId;
    @Column(nullable=false) boolean forwarded;
    @Column(nullable=false) boolean pinned;
    @ElementCollection @BatchSize(size=100)
    @CollectionTable(name="chat_reactions", joinColumns=@JoinColumn(name="message_id"))
    @MapKeyColumn(name="user_id") @Column(name="emoji",nullable=false,length=16)
    Map<Long,String> reactions = new HashMap<>();
    @ElementCollection @BatchSize(size=100)
    @CollectionTable(name="chat_favorites", joinColumns=@JoinColumn(name="message_id"),
        uniqueConstraints=@UniqueConstraint(columnNames={"message_id","user_id"}))
    @Column(name="user_id",nullable=false)
    Set<Long> favorites = new HashSet<>();
    protected ChatMessage() {}
    ChatMessage(String roomId, Long senderId, String text, String clientId) {
        this.roomId = roomId; this.senderId = senderId; this.text = text; this.clientId = clientId; createdAt = Instant.now();
    }
}
