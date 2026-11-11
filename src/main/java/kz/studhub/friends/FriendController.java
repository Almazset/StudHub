package kz.studhub.friends;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import kz.studhub.chat.ChatService;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/friends")
public class FriendController {
 private final FriendshipRepository friends; private final UserAccountRepository users; private final ChatService chat; private final PresenceService presence;
 public FriendController(FriendshipRepository f,UserAccountRepository u,ChatService c,PresenceService presence){friends=f;users=u;chat=c;this.presence=presence;}
 public record Request(@NotNull @Positive Long userId) {}
 private Long me(Principal p){return users.findByEmailIgnoreCase(p.getName()).orElseThrow().getId();}
 private ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND);}
 private Map<String,Object> view(Friendship f,Long viewer){Long peer=f.lowUserId.equals(viewer)?f.highUserId:f.lowUserId;var person=users.findChatIdentities(List.of(peer)).stream().findFirst().orElseThrow(this::notFound);String name=("members".equals(person.getProfileVisibility()) ? (person.getNickname()==null||person.getNickname().isBlank()?person.getFullName():person.getNickname()) : "Участник");return Map.of("id",f.id,"userId",peer,"name",name,"status",f.status,"incoming",!f.requesterId.equals(viewer));}
 @GetMapping @Transactional(readOnly=true) public List<Map<String,Object>> list(Principal p){Long id=me(p);var relationships=friends.visible(id);
 var accepted=relationships.stream().filter(f->"ACCEPTED".equals(f.status)).map(f->f.lowUserId.equals(id)?f.highUserId:f.lowUserId).toList();
 var statuses=presence.visibleStatuses(accepted);
 return relationships.stream().map(f->{Map<String,Object> result=new HashMap<>(view(f,id));Long peer=f.lowUserId.equals(id)?f.highUserId:f.lowUserId;
 result.put("presence",statuses.getOrDefault(peer,"HIDDEN"));return result;}).toList();}
 @PostMapping @Transactional public Map<String,Object> send(Principal principal,@Valid @RequestBody Request req){
 Long me=me(principal),peer=req.userId();if(me.equals(peer))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);Long lo=Math.min(me,peer),hi=Math.max(me,peer);
 users.findLockedById(lo).orElseThrow(this::notFound); var target=users.findById(peer).filter(u->"members".equals(u.getProfileVisibility())).orElseThrow(this::notFound);
 if(friends.findByLowUserIdAndHighUserId(lo,hi).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Заявка или дружба уже существует");
 Friendship f=new Friendship();f.lowUserId=lo;f.highUserId=hi;f.requesterId=me;f.status="PENDING";f.createdAt=Instant.now();friends.saveAndFlush(f);return view(f,me);
 }
 @PostMapping("/{id}/accept") @Transactional public Map<String,Object> accept(Principal p,@PathVariable Long id){Long me=me(p);Friendship f=friends.findById(id).orElseThrow(this::notFound);if(!f.lowUserId.equals(me)&&!f.highUserId.equals(me))throw notFound();if(f.requesterId.equals(me)||!f.status.equals("PENDING"))throw new ResponseStatusException(HttpStatus.CONFLICT);f.status="ACCEPTED";return view(friends.saveAndFlush(f),me);}
 @DeleteMapping("/{id}") @Transactional public Map<String,Boolean> remove(Principal p,@PathVariable Long id){Long me=me(p);Friendship f=friends.findById(id).orElseThrow(this::notFound);if(!f.lowUserId.equals(me)&&!f.highUserId.equals(me))throw notFound();friends.delete(f);return Map.of("ok",true);}
 @PostMapping("/{id}/chat") @Transactional public ChatService.Room openChat(Principal p,@PathVariable Long id){Long me=me(p);Friendship f=friends.findById(id).orElseThrow(this::notFound);if((!f.lowUserId.equals(me)&&!f.highUserId.equals(me))||!f.status.equals("ACCEPTED"))throw notFound();Long peer=f.lowUserId.equals(me)?f.highUserId:f.lowUserId;return chat.direct(p.getName(),peer);}
}
