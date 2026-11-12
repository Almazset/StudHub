package kz.studhub.chat;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(String roomId, long before, Pageable page);
    List<ChatMessage> findByRoomIdAndIdGreaterThanOrderByIdAsc(String roomId, long after, Pageable page);
    List<ChatMessage> findByRoomIdAndTextContainingIgnoreCaseOrderByIdDesc(String roomId,String text,Pageable page);
    Optional<ChatMessage> findFirstByRoomIdOrderByIdDesc(String roomId);
    Optional<ChatMessage> findByRoomIdAndSenderIdAndClientId(String roomId, Long senderId, String clientId);
    long countByRoomIdAndIdGreaterThanAndSenderIdNot(String roomId, long after, Long viewer);
    long countBySenderIdAndCreatedAtAfter(Long userId, Instant since);
    @Query("select m from ChatMessage m where m.id in (select max(last.id) from ChatMessage last where last.roomId in :rooms group by last.roomId)")
    List<ChatMessage> latestForRooms(@Param("rooms") Collection<String> rooms);
    interface Unread { String getRoomId(); long getTotal(); }
    @Query("select m.roomId as roomId, count(m) as total from ChatMessage m left join ChatMember p on p.roomId = m.roomId and p.userId = :viewer where m.roomId in :rooms and m.senderId <> :viewer and m.id > coalesce(p.lastRead, 0) group by m.roomId")
    List<Unread> unreadForRooms(@Param("rooms") Collection<String> rooms, @Param("viewer") Long viewer);
}
