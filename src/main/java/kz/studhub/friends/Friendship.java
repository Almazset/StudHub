package kz.studhub.friends;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="friendships",uniqueConstraints=@UniqueConstraint(columnNames={"low_user_id","high_user_id"}))
public class Friendship {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(name="low_user_id",nullable=false) Long lowUserId;
 @Column(name="high_user_id",nullable=false) Long highUserId;
 @Column(nullable=false) Long requesterId;
 @Column(nullable=false,length=12) String status;
 @Column(nullable=false) Instant createdAt;
 protected Friendship() {}
}
