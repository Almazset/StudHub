package kz.studhub.project;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ProjectRepository extends JpaRepository<Project,Long> {
    @Query("select distinct p from Project p left join p.memberIds m where p.ownerId = :userId or m = :userId order by p.id desc")
    List<Project> visible(@Param("userId") Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> locked(@Param("id") Long id);
}
