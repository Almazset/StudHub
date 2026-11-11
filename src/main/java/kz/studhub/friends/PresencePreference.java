package kz.studhub.friends;

import jakarta.persistence.*;

@Entity
@Table(name="friend_presence_preferences")
public class PresencePreference {
    @Id Long userId;
    @Column(nullable=false,length=12) String status;
    protected PresencePreference() {}
    PresencePreference(Long userId,String status) { this.userId=userId;this.status=status; }
}
