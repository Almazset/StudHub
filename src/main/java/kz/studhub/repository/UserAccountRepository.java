package kz.studhub.repository;
import kz.studhub.model.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("update UserAccount u set u.fullName = :name where lower(u.email) = lower(:email)")
    int updateFullName(@Param("email") String email, @Param("name") String name);
    @Query("select u.id from UserAccount u where u.id in :ids and u.presenceVisibility = 'friends'")
    java.util.List<Long> findIdsSharingPresence(@Param("ids") java.util.Collection<Long> ids);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where lower(u.email) = lower(:email)")
    Optional<UserAccount> findLockedByEmail(@Param("email") String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findLockedById(@Param("id") Long id);
    interface ChatIdentity {
        Long getId();
        String getFullName();
        String getNickname();
        String getProfileVisibility();
        String getDirectMessages();
    }
    @Query("select u.id as id, u.fullName as fullName, u.nickname as nickname, u.profileVisibility as profileVisibility, u.directMessages as directMessages from UserAccount u where u.id in :ids")
    java.util.List<ChatIdentity> findChatIdentities(@Param("ids") java.util.Collection<Long> ids);
    @Query("""
        select u.id as id, u.fullName as fullName, u.nickname as nickname from UserAccount u
        where u.id <> :viewer and u.id not in :blocked
          and (u.profileVisibility = 'members' or (u.profileVisibility = 'friends' and u.id in :friends))
          and (lower(u.fullName) like lower(concat('%', :name, '%')) or lower(u.nickname) like lower(concat('%', :name, '%')))
        order by u.fullName, u.id
        """)
    java.util.List<ChatIdentity> searchChatUsers(@Param("name") String name, @Param("viewer") Long viewer,
        @Param("blocked") java.util.Collection<Long> blocked, @Param("friends") java.util.Collection<Long> friends,
        org.springframework.data.domain.Pageable page);
}
