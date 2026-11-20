package kz.studhub.friends;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ContactBlockRepository extends JpaRepository<ContactBlock, Long> {
    Optional<ContactBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
    List<ContactBlock> findByBlockerIdOrderByCreatedAtDescIdDesc(Long blockerId);
    @Query("select count(b) > 0 from ContactBlock b where (b.blockerId = :first and b.blockedId = :second) "
        + "or (b.blockerId = :second and b.blockedId = :first)")
    boolean existsBetween(@Param("first") Long first, @Param("second") Long second);

    @Query("select case when b.blockerId = :viewer then b.blockedId else b.blockerId end from ContactBlock b "
        + "where b.blockerId = :viewer or b.blockedId = :viewer")
    List<Long> peers(@Param("viewer") Long viewer);
}
