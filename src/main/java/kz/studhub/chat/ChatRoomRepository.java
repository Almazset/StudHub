package kz.studhub.chat;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {
    Optional<ChatRoom> findByDirectKey(String key);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRoom r where r.id = :id")
    Optional<ChatRoom> locked(@Param("id") String id);
    @Query("select r from ChatRoom r where r.id = 'global' or r.id in (select m.roomId from ChatMember m where m.userId = :userId) order by r.updatedAt desc, r.id")
    List<ChatRoom> visible(@Param("userId") Long userId, Pageable page);
}
