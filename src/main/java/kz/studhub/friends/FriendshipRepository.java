package kz.studhub.friends;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface FriendshipRepository extends JpaRepository<Friendship,Long> {
 Optional<Friendship> findByLowUserIdAndHighUserId(Long low,Long high);
 @Query("select f from Friendship f where f.lowUserId=:id or f.highUserId=:id order by f.createdAt desc") List<Friendship> visible(@Param("id") Long id);
 @Query("select case when f.lowUserId = :viewer then f.highUserId else f.lowUserId end "
     + "from Friendship f where f.id = :id and (f.lowUserId = :viewer or f.highUserId = :viewer)")
 Optional<Long> findPeer(@Param("id") Long id, @Param("viewer") Long viewer);
 @Query("select case when f.lowUserId = :viewer then f.highUserId else f.lowUserId end from Friendship f "
     + "where f.status = 'ACCEPTED' and (f.lowUserId = :viewer or f.highUserId = :viewer)")
 Set<Long> acceptedPeers(@Param("viewer") Long viewer);
}
