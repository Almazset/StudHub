package kz.studhub.repository;
import kz.studhub.model.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where lower(u.email) = lower(:email)")
    Optional<UserAccount> findLockedByEmail(@Param("email") String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findLockedById(@Param("id") Long id);
    interface ChatIdentity { Long getId(); String getFullName(); String getNickname(); String getProfileVisibility(); }
    @Query("select u.id as id, u.fullName as fullName, u.nickname as nickname, u.profileVisibility as profileVisibility from UserAccount u where u.id in :ids")
    java.util.List<ChatIdentity> findChatIdentities(@Param("ids") java.util.Collection<Long> ids);
    @Query("select u.id as id, u.fullName as fullName, u.nickname as nickname, u.profileVisibility as profileVisibility from UserAccount u where u.profileVisibility = :visibility and u.id <> :id and (lower(u.fullName) like lower(concat('%', :name, '%')) or lower(u.nickname) like lower(concat('%', :name, '%'))) order by u.fullName, u.id")
    java.util.List<ChatIdentity> searchChatUsers(@Param("visibility") String visibility, @Param("name") String name, @Param("id") Long id, org.springframework.data.domain.Pageable page);
}
