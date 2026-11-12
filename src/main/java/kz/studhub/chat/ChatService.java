package kz.studhub.chat;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import kz.studhub.repository.UserAccountRepository.ChatIdentity;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChatService {
    public record Person(Long id, String name) {}
    public record Message(Long id, Person sender, String text, Instant createdAt, String clientId, Instant editedAt) {}
    public record Room(String id, String kind, String name, Long peerId, Long ownerId, int memberCount,
                       long unread, Message lastMessage, Instant updatedAt) {}
    public record RoomList(Long meId, List<Room> rooms) {}
    public record Detail(Room room, List<Person> members) {}
    public record History(List<Message> messages, boolean hasMore) {}
    public static class Failure extends RuntimeException {
        final HttpStatus status; final String code;
        Failure(HttpStatus status, String code) { super(code); this.status = status; this.code = code; }
    }
    private final ChatRoomRepository rooms;
    private final ChatMemberRepository members;
    private final ChatMessageRepository messages;
    private final UserAccountRepository users;
    public ChatService(ChatRoomRepository rooms, ChatMemberRepository members, ChatMessageRepository messages, UserAccountRepository users) {
        this.rooms = rooms; this.members = members; this.messages = messages; this.users = users;
    }
    private Failure fail(HttpStatus status, String code) { return new Failure(status, code); }
    private UserAccount me(String email) { return users.findByEmailIgnoreCase(email).orElseThrow(() -> fail(HttpStatus.UNAUTHORIZED,"LOGIN")); }
    private UserAccount lockedMe(String email) { return users.findLockedByEmail(email).orElseThrow(() -> fail(HttpStatus.UNAUTHORIZED,"LOGIN")); }
    private ChatRoom accessible(String id, Long userId, boolean lock) {
        ChatRoom room = (lock ? rooms.locked(id) : rooms.findById(id)).orElseThrow(() -> fail(HttpStatus.NOT_FOUND,"NOT_FOUND"));
        if (!room.kind.equals("GLOBAL") && !members.existsByRoomIdAndUserId(id,userId)) throw fail(HttpStatus.NOT_FOUND,"NOT_FOUND");
        return room;
    }
    private Person person(Long id, Map<Long,ChatIdentity> accounts, Long viewer) {
        ChatIdentity user = accounts.get(id);
        return new Person(id, user != null && (id.equals(viewer) || "members".equals(user.getProfileVisibility())) ? displayName(user) : null);
    }
    private String displayName(ChatIdentity user) { return user.getNickname() == null || user.getNickname().isBlank() ? user.getFullName() : user.getNickname(); }
    private Map<Long,ChatIdentity> accounts(Collection<Long> ids) {
        return ids.isEmpty() ? Map.of() : users.findChatIdentities(new HashSet<>(ids)).stream().collect(Collectors.toMap(ChatIdentity::getId,u -> u));
    }
    private List<Message> views(List<ChatMessage> rows, Long viewer) {
        var accounts = accounts(rows.stream().map(m -> m.senderId).toList());
        return rows.stream().map(m -> new Message(m.id,person(m.senderId,accounts,viewer),m.text,m.createdAt,m.clientId,m.editedAt)).toList();
    }
    private Room view(ChatRoom room, Long viewer) {
        var participants = members.findByRoomIdOrderByUserId(room.id);
        Long peerId = room.kind.equals("DIRECT") ? participants.stream().map(m -> m.userId).filter(id -> !id.equals(viewer)).findFirst().orElse(null) : null;
        String name = peerId == null ? room.name : person(peerId,accounts(List.of(peerId)),viewer).name();
        long read = participants.stream().filter(m -> m.userId.equals(viewer)).mapToLong(m -> m.lastRead).findFirst().orElse(0);
        var last = messages.findFirstByRoomIdOrderByIdDesc(room.id);
        return new Room(room.id,room.kind,name,peerId,room.ownerId,room.kind.equals("GLOBAL") ? 0 : participants.size(),
            messages.countByRoomIdAndIdGreaterThanAndSenderIdNot(room.id,read,viewer),
            last.map(m -> views(List.of(m),viewer).getFirst()).orElse(null),room.updatedAt);
    }
    @Transactional(readOnly = true)
    public RoomList list(String email) {
        Long viewer = me(email).getId();
        var visible = rooms.visible(viewer,PageRequest.of(0,101));
        var ids = visible.stream().map(r -> r.id).toList();
        if (ids.isEmpty()) return new RoomList(viewer,List.of());
        var people = members.forRooms(ids,viewer).stream().collect(Collectors.groupingBy(m -> m.roomId));
        var latest = messages.latestForRooms(ids).stream().collect(Collectors.toMap(m -> m.roomId,m -> m));
        var unread = messages.unreadForRooms(ids,viewer).stream().collect(Collectors.toMap(ChatMessageRepository.Unread::getRoomId,ChatMessageRepository.Unread::getTotal));
        var userIds = new HashSet<Long>(); people.values().forEach(list -> list.forEach(m -> userIds.add(m.userId))); latest.values().forEach(m -> userIds.add(m.senderId));
        var accounts = accounts(userIds);
        return new RoomList(viewer,visible.stream().map(room -> {
            var participants = people.getOrDefault(room.id,List.of());
            Long peerId = room.kind.equals("DIRECT") ? participants.stream().map(m -> m.userId).filter(uid -> !uid.equals(viewer)).findFirst().orElse(null) : null;
            String name = peerId == null ? room.name : person(peerId,accounts,viewer).name();
            ChatMessage last = latest.get(room.id);
            return new Room(room.id,room.kind,name,peerId,room.ownerId,room.kind.equals("GLOBAL") ? 0 : participants.size(),unread.getOrDefault(room.id,0L),
                last == null ? null : new Message(last.id,person(last.senderId,accounts,viewer),last.text,last.createdAt,last.clientId,last.editedAt),room.updatedAt);
        }).toList());
    }
    @Transactional(readOnly = true)
    public List<Person> search(String email, String query) {
        var viewer = me(email);
        String term = query.strip();
        if (term.regionMatches(true, 0, "SH-", 0, 3)) {
            String number = term.substring(3);
            if (!number.matches("[1-9][0-9]{0,18}")) return List.of();
            try {
                Long id = Long.valueOf(number);
                if (id.equals(viewer.getId())) return List.of();
                return users.findChatIdentities(List.of(id)).stream()
                    .filter(u -> "members".equals(u.getProfileVisibility()))
                    .map(u -> new Person(u.getId(), displayName(u))).toList();
            } catch (NumberFormatException ex) { return List.of(); }
        }
        return users.searchChatUsers("members",term,viewer.getId(),PageRequest.of(0,20))
            .stream().map(u -> new Person(u.getId(),displayName(u))).toList();
    }
    @Transactional(readOnly = true)
    public Detail detail(String email, String id) {
        Long viewer = me(email).getId(); var room = accessible(id,viewer,false);
        var ids = room.kind.equals("GLOBAL") ? List.<Long>of() : members.findByRoomIdOrderByUserId(id).stream().map(m -> m.userId).toList();
        var accounts = accounts(ids);
        return new Detail(view(room,viewer),ids.stream().map(uid -> person(uid,accounts,viewer)).toList());
    }
    private void capacity(Long id) {
        if (members.countByUserId(id) >= 100) throw fail(HttpStatus.CONFLICT,"ROOM_LIMIT");
    }
    private void selectable(Collection<Long> ids, Long viewer) {
        var found = accounts(ids);
        if (ids.stream().anyMatch(id -> id.equals(viewer) || !found.containsKey(id) || !"members".equals(found.get(id).getProfileVisibility())))
            throw fail(HttpStatus.BAD_REQUEST,"MEMBER_UNAVAILABLE");
    }
    public Room direct(String email, Long peerId) {
        Long viewer = me(email).getId();
        if (viewer.equals(peerId)) throw fail(HttpStatus.BAD_REQUEST,"SELF_CHAT");
        // Serialize creation of the same pair before checking its unique key.
        users.findLockedById(Math.min(viewer,peerId)).orElseThrow(() -> fail(HttpStatus.BAD_REQUEST,"MEMBER_UNAVAILABLE"));
        String key = Math.min(viewer,peerId) + ":" + Math.max(viewer,peerId);
        var existing = rooms.findByDirectKey(key);
        if (existing.isPresent()) return view(existing.get(),viewer);
        selectable(List.of(peerId),viewer); capacity(viewer); capacity(peerId);
        var room = new ChatRoom(UUID.randomUUID().toString(),"DIRECT","",null); room.directKey = key;
        rooms.save(room); members.save(new ChatMember(room.id,viewer)); members.save(new ChatMember(room.id,peerId));
        return view(room,viewer);
    }
    private void projectCapacity(Long userId) {
        if (members.countByUserId(userId) >= 100)
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,"Достигнут лимит чатов участника");
    }
    // Called only after the project controller has locked and authorized the project.
    public Room projectRoom(Long projectId, String title, Long ownerId, Set<Long> userIds, Long viewer, boolean create) {
        String id = "project-" + projectId;
        ChatRoom room = rooms.locked(id).orElse(null);
        if (room == null && !create) return null;
        if (room == null) {
            userIds.forEach(this::projectCapacity);
            room = rooms.save(new ChatRoom(id,"PROJECT",title.substring(0,Math.min(80,title.length())),ownerId));
        }
        room.name=title.substring(0,Math.min(80,title.length()));
        members.findByRoomIdOrderByUserId(id).stream().filter(m -> !userIds.contains(m.userId)).forEach(members::delete);
        for (Long userId : userIds) {
            if (!members.existsByRoomIdAndUserId(id,userId)) {
                projectCapacity(userId);
                members.save(new ChatMember(id,userId));
            }
        }
        return view(room,viewer);
    }
    public Room group(String email, String name, Set<Long> ids) {
        Long viewer = lockedMe(email).getId();
        String title = name.strip();
        if (title.length() < 2) throw fail(HttpStatus.BAD_REQUEST,"VALIDATION");
        selectable(ids,viewer); capacity(viewer); ids.forEach(this::capacity);
        var room = rooms.save(new ChatRoom(UUID.randomUUID().toString(),"GROUP",title,viewer));
        members.save(new ChatMember(room.id,viewer)); ids.forEach(id -> members.save(new ChatMember(room.id,id)));
        return view(room,viewer);
    }
    @Transactional(readOnly = true)
    public History history(String email, String id, Long before, Long after) {
        Long viewer = me(email).getId(); accessible(id,viewer,false);
        if (before != null && after != null) throw fail(HttpStatus.BAD_REQUEST,"VALIDATION");
        var rows = new ArrayList<>(after == null
            ? messages.findByRoomIdAndIdLessThanOrderByIdDesc(id,before == null ? Long.MAX_VALUE : before,PageRequest.of(0,51))
            : messages.findByRoomIdAndIdGreaterThanOrderByIdAsc(id,after,PageRequest.of(0,51)));
        boolean more = rows.size() > 50;
        if (more) rows.removeLast();
        if (after == null) Collections.reverse(rows);
        return new History(views(rows,viewer),more);
    }
    public Message send(String email, String id, String text, UUID clientId) {
        Long viewer = lockedMe(email).getId(); var room = accessible(id,viewer,true);
        var previous = messages.findByRoomIdAndSenderIdAndClientId(id,viewer,clientId.toString());
        if (previous.isPresent()) return views(List.of(previous.get()),viewer).getFirst();
        String body = text.strip();
        if (body.isEmpty() || body.length() > 4000 || body.indexOf('\0') >= 0) throw fail(HttpStatus.BAD_REQUEST,"VALIDATION");
        if (messages.countBySenderIdAndCreatedAtAfter(viewer,Instant.now().minusSeconds(60)) >= 30) throw fail(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMIT");
        var message = messages.saveAndFlush(new ChatMessage(id,viewer,body,clientId.toString()));
        room.updatedAt = message.createdAt;
        return views(List.of(message),viewer).getFirst();
    }
    public Message editMessage(String email, String id, Long messageId, String text) {
        Long viewer=me(email).getId(); accessible(id,viewer,true);
        var message=messages.findById(messageId).filter(m -> m.roomId.equals(id)).orElseThrow(() -> fail(HttpStatus.NOT_FOUND,"NOT_FOUND"));
        if(!message.senderId.equals(viewer)) throw fail(HttpStatus.FORBIDDEN,"OWNER_ONLY");
        String body=text.strip();if(body.isEmpty() || body.length()>4000 || body.indexOf('\0')>=0) throw fail(HttpStatus.BAD_REQUEST,"VALIDATION");
        message.text=body;message.editedAt=Instant.now();return views(List.of(message),viewer).getFirst();
    }
    @Transactional(readOnly=true)
    public List<Message> searchMessages(String email,String id,String query) {
        Long viewer=me(email).getId();accessible(id,viewer,false);
        String term=query.strip();if(term.length()<2 || term.length()>120) throw fail(HttpStatus.BAD_REQUEST,"VALIDATION");
        return views(messages.findByRoomIdAndTextContainingIgnoreCaseOrderByIdDesc(id,term,PageRequest.of(0,50)),viewer);
    }
    public void read(String email, String id, long messageId) {
        Long viewer = lockedMe(email).getId(); accessible(id,viewer,true);
        var message = messages.findById(messageId).filter(m -> m.roomId.equals(id)).orElseThrow(() -> fail(HttpStatus.BAD_REQUEST,"VALIDATION"));
        var member = members.findByRoomIdAndUserId(id,viewer).orElseGet(() -> new ChatMember(id,viewer));
        member.lastRead = Math.max(member.lastRead,message.id); members.save(member);
    }
    public void addMembers(String email, String id, Set<Long> ids) {
        Long viewer = me(email).getId(); var room = accessible(id,viewer,true);
        if (!room.kind.equals("GROUP") || !viewer.equals(room.ownerId)) throw fail(HttpStatus.FORBIDDEN,"OWNER_ONLY");
        var current = members.findByRoomIdOrderByUserId(id).stream().map(m -> m.userId).collect(Collectors.toSet());
        var added = new HashSet<>(ids); added.removeAll(current);
        if (current.size() + added.size() > 30) throw fail(HttpStatus.BAD_REQUEST,"MEMBER_LIMIT");
        selectable(added,viewer); added.forEach(this::capacity);
        added.forEach(uid -> members.save(new ChatMember(id,uid)));
    }
    public void leave(String email, String id) {
        Long viewer = me(email).getId(); var room = accessible(id,viewer,true);
        if (!room.kind.equals("GROUP")) throw fail(HttpStatus.BAD_REQUEST,"GROUP_ONLY");
        var current = members.findByRoomIdOrderByUserId(id);
        members.delete(current.stream().filter(m -> m.userId.equals(viewer)).findFirst().orElseThrow());
        if (viewer.equals(room.ownerId)) room.ownerId = current.stream().map(m -> m.userId).filter(uid -> !uid.equals(viewer)).findFirst().orElse(null);
    }
}
