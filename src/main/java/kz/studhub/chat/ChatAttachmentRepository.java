package kz.studhub.chat;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {
    interface Metadata {
        Long getId();
        String getName();
        String getMediaType();
        int getSize();
    }

    @Query("select a.id as id, a.name as name, a.mediaType as mediaType, a.size as size from ChatAttachment a where a.id in :ids")
    List<Metadata> metadata(Collection<Long> ids);

    @Query("select coalesce(sum(a.size), 0) from ChatAttachment a join a.message m where m.senderId = :sender")
    long bytesUsed(Long sender);

    @Modifying
    @Query("delete from ChatAttachment a where a.id = :id")
    void deleteForMessage(Long id);
}
