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
    List<ChatMessage> findByRoomIdAndIdInOrderByIdAsc(String roomId,Collection<Long> ids);
    List<ChatMessage> findByRoomIdAndPinnedTrueOrderByIdDesc(String roomId,Pageable page);
    boolean existsByRoomIdAndIdLessThan(String roomId,Long id);
    @Query("""
        select count(message) > 0 from ChatMessage message join ChatRoom room on room.id = message.roomId
        where message.id = :messageId and message.senderId = :target
          and (room.kind = 'GLOBAL' or exists (
              select 1 from ChatMember member where member.roomId = room.id and member.userId = :viewer))
        """)
    boolean hasVisibleMessageFrom(@Param("viewer") Long viewer, @Param("target") Long target,
        @Param("messageId") Long messageId);
    @Query("select m from ChatMessage m join m.favorites f where m.roomId=:room and f=:viewer order by m.id desc")
    List<ChatMessage> favorites(@Param("room") String room,@Param("viewer") Long viewer,Pageable page);
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
