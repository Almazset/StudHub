package kz.studhub.chat;

import java.util.*;
import kz.studhub.friends.ContactPrivacyService;
import kz.studhub.model.UserAccount;
import kz.studhub.notification.NotificationService;
import kz.studhub.repository.UserAccountRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql=false,properties={"spring.datasource.url=jdbc:h2:mem:chat;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false"})
@Import({ChatService.class, ContactPrivacyService.class})
class ChatServiceTest {
    @Autowired ChatService chat;
    @MockitoBean NotificationService notifications;
    @Autowired UserAccountRepository users;
    @Autowired ChatRoomRepository rooms;
    @Autowired ChatMemberRepository members;
    @Autowired ChatMessageRepository messages;
    UserAccount alice, bob, stranger;
    UserAccount account(String name) {
        var user = new UserAccount(); user.setFullName(name); user.setEmail(name.toLowerCase()+"@example.com"); user.setPassword("test-hash-12345"); user.setProfileVisibility("members");
        return users.saveAndFlush(user);
    }
    @BeforeEach void setup() {
        alice = account("Alice"); bob = account("Bob"); stranger = account("Stranger");
        rooms.saveAndFlush(new ChatRoom("global","GLOBAL","StudHub",null));
    }
    void denied(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ChatService.Failure.class,error -> assertThat(error.status).isEqualTo(HttpStatus.NOT_FOUND));
    }
    @Test void directIsUniqueAndMessagesArePrivateEvenForAdmin() {
        var room = chat.direct(alice.getEmail(),bob.getId());
        assertThat(chat.direct(bob.getEmail(),alice.getId()).id()).isEqualTo(room.id());
        var sent = chat.send(alice.getEmail(),room.id(),"Lecture notes",UUID.randomUUID());
        assertThat(chat.history(bob.getEmail(),room.id(),null,null).messages()).extracting(ChatService.Message::text).containsExactly("Lecture notes");
        stranger.setRole(kz.studhub.model.Role.ADMIN);
        denied(() -> chat.history(stranger.getEmail(),room.id(),null,null));
        denied(() -> chat.send(stranger.getEmail(),room.id(),"Intrusion",UUID.randomUUID()));
        denied(() -> chat.detail(stranger.getEmail(),room.id()));
        denied(() -> chat.read(stranger.getEmail(),room.id(),sent.id()));
        assertThat(chat.list(stranger.getEmail()).rooms()).extracting(ChatService.Room::id).doesNotContain(room.id());
    }
    @Test void allAccountsCanReadAndWriteGlobalAndRetryIsIdempotent() {
        var retryId = UUID.randomUUID();
        var first = chat.send(alice.getEmail(),"global","https://example.com/lecture",retryId);
        assertThat(chat.send(alice.getEmail(),"global","https://example.com/lecture",retryId).id()).isEqualTo(first.id());
        assertThat(chat.history(stranger.getEmail(),"global",null,null).messages()).hasSize(1);
        assertThat(chat.detail(bob.getEmail(),"global").room().unread()).isEqualTo(1);
        chat.read(bob.getEmail(),"global",first.id());
        assertThat(chat.detail(bob.getEmail(),"global").room().unread()).isZero();
    }
    @Test void onlyAuthorCanDeleteTheirMessage() {
        var room=chat.direct(alice.getEmail(),bob.getId());
        var message=chat.send(alice.getEmail(),room.id(),"Hello",UUID.randomUUID());
        assertThatThrownBy(() -> chat.deleteMessage(bob.getEmail(),room.id(),message.id()))
            .isInstanceOfSatisfying(ChatService.Failure.class,e -> assertThat(e.status).isEqualTo(HttpStatus.FORBIDDEN));
        denied(() -> chat.deleteMessage(stranger.getEmail(),room.id(),message.id()));
        denied(() -> chat.deleteMessage(alice.getEmail(),"global",message.id()));
        chat.deleteMessage(alice.getEmail(),room.id(),message.id());
        assertThat(chat.history(bob.getEmail(),room.id(),null,null).messages()).isEmpty();
    }
    @Test void groupsEnforceOwnerMembershipAndLeaveRevokesAccess() {
        var room = chat.group(alice.getEmail(),"Java study",Set.of(bob.getId()));
        assertThatThrownBy(() -> chat.addMembers(bob.getEmail(),room.id(),Set.of(stranger.getId())))
            .isInstanceOfSatisfying(ChatService.Failure.class,e -> assertThat(e.code).isEqualTo("OWNER_ONLY"));
        chat.addMembers(alice.getEmail(),room.id(),Set.of(stranger.getId()));
        assertThat(chat.detail(stranger.getEmail(),room.id()).members()).hasSize(3);
        chat.leave(alice.getEmail(),room.id());
        denied(() -> chat.history(alice.getEmail(),room.id(),null,null));
        assertThat(chat.detail(bob.getEmail(),room.id()).room().ownerId()).isEqualTo(bob.getId());
        chat.leave(stranger.getEmail(),room.id());
        denied(() -> chat.send(stranger.getEmail(),room.id(),"Former member",UUID.randomUUID()));
    }
    @Test void privateNamesAreNeverExposedBySearchRoomOrMessage() {
        var room = chat.direct(alice.getEmail(),bob.getId());
        bob.setProfileVisibility("private"); users.saveAndFlush(bob);
        assertThat(chat.search(alice.getEmail(),"Bob")).isEmpty();
        chat.send(bob.getEmail(),room.id(),"Hello",UUID.randomUUID());
        assertThat(chat.detail(alice.getEmail(),room.id()).room().name()).isNull();
        assertThat(chat.history(alice.getEmail(),room.id(),null,null).messages().getFirst().sender().name()).isNull();
        assertThatThrownBy(() -> chat.direct(stranger.getEmail(),bob.getId())).isInstanceOf(ChatService.Failure.class);
    }
    @Test void historyPaginationHasNoGapsAndReadCannotMoveBackwardsOrAcrossRooms() {
        for (int i=0;i<105;i++) messages.save(new ChatMessage("global",alice.getId(),"Message "+i,UUID.randomUUID().toString()));
        messages.flush();
        var latest = chat.history(bob.getEmail(),"global",null,null);
        assertThat(latest.messages()).hasSize(50); assertThat(latest.hasMore()).isTrue();
        var older = chat.history(bob.getEmail(),"global",latest.messages().getFirst().id(),null);
        var oldest = chat.history(bob.getEmail(),"global",older.messages().getFirst().id(),null);
        assertThat(oldest.messages()).hasSize(5); assertThat(oldest.hasMore()).isFalse();
        var forward = chat.history(bob.getEmail(),"global",null,oldest.messages().getLast().id());
        assertThat(forward.messages()).isEqualTo(older.messages()); assertThat(forward.hasMore()).isTrue();
        chat.read(bob.getEmail(),"global",latest.messages().getLast().id());
        chat.read(bob.getEmail(),"global",oldest.messages().getFirst().id());
        assertThat(chat.detail(bob.getEmail(),"global").room().unread()).isZero();
        var room = chat.direct(alice.getEmail(),bob.getId());
        assertThatThrownBy(() -> chat.read(bob.getEmail(),room.id(),latest.messages().getLast().id())).isInstanceOf(ChatService.Failure.class);
    }
    @Test void limitsAndBlankMessagesAreRejected() {
        assertThatThrownBy(() -> chat.send(alice.getEmail(),"global","  ",UUID.randomUUID())).isInstanceOf(ChatService.Failure.class);
        assertThatThrownBy(() -> chat.send(alice.getEmail(),"global","a\0b",UUID.randomUUID())).isInstanceOf(ChatService.Failure.class);
        for (int i=0;i<30;i++) chat.send(alice.getEmail(),"global","Message "+i,UUID.randomUUID());
        assertThatThrownBy(() -> chat.send(alice.getEmail(),"global","Too many",UUID.randomUUID()))
            .isInstanceOfSatisfying(ChatService.Failure.class,e -> assertThat(e.status).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(chat.send(bob.getEmail(),"global","Different account",UUID.randomUUID()).id()).isNotNull();
    }

    @Test void repliesFollowEditsAndShowDeletedOriginalWithoutLeakingOtherRooms() {
        String room=chat.direct(alice.getEmail(),bob.getId()).id();
        var original=chat.send(alice.getEmail(),room,"First",UUID.randomUUID());
        denied(()->chat.send(bob.getEmail(),"global","Leak",UUID.randomUUID(),original.id()));
        var reply=chat.send(bob.getEmail(),room,"Answer",UUID.randomUUID(),original.id());
        assertThat(reply.reply().text()).isEqualTo("First");
        chat.editMessage(alice.getEmail(),room,original.id(),"Revised");
        assertThat(chat.refresh(bob.getEmail(),room,Set.of(reply.id())).getFirst().reply().text()).isEqualTo("Revised");
        chat.deleteMessage(alice.getEmail(),room,original.id());
        assertThat(chat.refresh(bob.getEmail(),room,Set.of(reply.id())).getFirst().reply().text()).isNull();
    }
    @Test void reactionsAreIdempotentFavoritesPrivateAndPinsRequireAuthority() {
        String room=chat.group(alice.getEmail(),"Study",Set.of(bob.getId())).id();
        var message=chat.send(alice.getEmail(),room,"Notes",UUID.randomUUID());
        chat.reaction(bob.getEmail(),room,message.id(),"👍");
        assertThat(chat.reaction(bob.getEmail(),room,message.id(),"👍").reactions().getFirst().count()).isEqualTo(1);
        assertThat(chat.reaction(alice.getEmail(),room,message.id(),"👍").reactions().getFirst().count()).isEqualTo(2);
        chat.reaction(bob.getEmail(),room,message.id(),"");
        assertThat(chat.refresh(bob.getEmail(),room,Set.of(message.id())).getFirst().reactions().getFirst().mine()).isFalse();
        chat.favorite(bob.getEmail(),room,message.id(),true);
        assertThat(chat.saved(bob.getEmail(),room,"favorites")).hasSize(1);
        assertThat(chat.saved(alice.getEmail(),room,"favorites")).isEmpty();
        denied(()->chat.favorite(stranger.getEmail(),room,message.id(),true));
        assertThatThrownBy(()->chat.pin(bob.getEmail(),room,message.id(),true)).isInstanceOf(ChatService.Failure.class);
        assertThat(chat.pin(alice.getEmail(),room,message.id(),true).pinned()).isTrue();
        assertThat(chat.saved(bob.getEmail(),room,"pins")).hasSize(1);
        chat.leave(bob.getEmail(),room);
        denied(()->chat.saved(bob.getEmail(),room,"favorites"));
        chat.deleteMessage(alice.getEmail(),room,message.id());messages.flush();
        assertThat(chat.saved(alice.getEmail(),room,"pins")).isEmpty();
    }
    @Test void forwardChecksBothRoomsAndPersistsIndependentCopyWithRetryProtection() {
        String room=chat.direct(alice.getEmail(),bob.getId()).id();
        var message=chat.send(alice.getEmail(),room,"Shared text",UUID.randomUUID());
        denied(()->chat.forward(stranger.getEmail(),room,message.id(),"global",UUID.randomUUID()));
        String privateRoom=chat.direct(bob.getEmail(),stranger.getId()).id();
        denied(()->chat.forward(alice.getEmail(),room,message.id(),privateRoom,UUID.randomUUID()));
        UUID retry=UUID.randomUUID();var forwarded=chat.forward(bob.getEmail(),room,message.id(),"global",retry);
        assertThat(forwarded.forwarded()).isTrue();assertThat(forwarded.reply()).isNull();
        assertThat(chat.forward(bob.getEmail(),room,message.id(),"global",retry).id()).isEqualTo(forwarded.id());
        chat.deleteMessage(alice.getEmail(),room,message.id());
        assertThat(chat.history(stranger.getEmail(),"global",null,null).messages().getFirst().text()).isEqualTo("Shared text");
    }
    @Test void refreshCoversOldEditsAndDeletionAndAroundDoesNotMarkRead() {
        for(int i=0;i<75;i++)messages.save(new ChatMessage("global",alice.getId(),"Needle "+i,UUID.randomUUID().toString()));
        messages.flush();var old=chat.history(bob.getEmail(),"global",null,0L).messages().getFirst();
        chat.editMessage(alice.getEmail(),"global",old.id(),"Old revised");
        assertThat(chat.refresh(bob.getEmail(),"global",Set.of(old.id())).getFirst().text()).isEqualTo("Old revised");
        assertThat(chat.around(bob.getEmail(),"global",old.id()).messages()).extracting(ChatService.Message::id).contains(old.id());
        assertThat(chat.detail(bob.getEmail(),"global").room().unread()).isEqualTo(75);
        chat.deleteMessage(alice.getEmail(),"global",old.id());
        assertThat(chat.refresh(bob.getEmail(),"global",Set.of(old.id()))).isEmpty();
        assertThat(chat.detail(bob.getEmail(),"global").room().unread()).isEqualTo(74);
    }
}
