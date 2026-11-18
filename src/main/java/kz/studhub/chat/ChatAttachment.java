package kz.studhub.chat;

import jakarta.persistence.*;

@Entity
@Table(name = "chat_attachments")
class ChatAttachment {
    @Id Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id")
    ChatMessage message;

    @Column(nullable = false, length = 120) String name;
    @Column(nullable = false, length = 100) String mediaType;
    @Column(nullable = false) int size;
    @Column(nullable = false, columnDefinition = "bytea") byte[] data;

    protected ChatAttachment() {}

    ChatAttachment(ChatMessage message, ChatFileValidator.File file) {
        this.message = message;
        this.name = file.name();
        this.mediaType = file.mediaType();
        this.data = file.data();
        this.size = data.length;
    }

    ChatFileValidator.File file() {
        return new ChatFileValidator.File(name, mediaType, data);
    }
}
