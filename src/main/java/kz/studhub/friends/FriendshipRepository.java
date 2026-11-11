package kz.studhub.friends;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface FriendshipRepository extends JpaRepository<Friendship,Long> {
 Optional<Friendship> findByLowUserIdAndHighUserId(Long low,Long high);
 @Query("select f from Friendship f where f.lowUserId=:id or f.highUserId=:id order by f.createdAt desc") List<Friendship> visible(@Param("id") Long id);
}
