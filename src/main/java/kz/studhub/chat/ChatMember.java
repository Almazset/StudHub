package kz.studhub.chat;

import jakarta.persistence.*;

@Entity
@Table(name = "chat_members", indexes = @Index(name = "chat_member_user", columnList = "userId,roomId"))
public class ChatMember {
    @Id @Column(length = 60) String id;
    @Column(nullable = false, length = 36) String roomId;
    @Column(nullable = false) Long userId;
    @Column(nullable = false) long lastRead;
    protected ChatMember() {}
    ChatMember(String roomId, Long userId) { this.id = roomId + ":" + userId; this.roomId = roomId; this.userId = userId; }
}
