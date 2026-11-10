package kz.studhub.chat;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMemberRepository extends JpaRepository<ChatMember, String> {
    List<ChatMember> findByRoomIdOrderByUserId(String roomId);
    Optional<ChatMember> findByRoomIdAndUserId(String roomId, Long userId);
    boolean existsByRoomIdAndUserId(String roomId, Long userId);
    long countByUserId(Long userId);
    @Query("select m from ChatMember m where m.roomId in :rooms and (m.roomId <> 'global' or m.userId = :viewer)")
    List<ChatMember> forRooms(@Param("rooms") Collection<String> rooms, @Param("viewer") Long viewer);
}
