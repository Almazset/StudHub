package kz.studhub.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import kz.studhub.notification.NotificationService;
import kz.studhub.notification.NotificationType;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectRepository projects;
    private final UserAccountRepository users;
    private final kz.studhub.chat.ChatService chat;
    private final ProjectTaskRepository tasks;
    private final ProjectHistory history;
    private final NotificationService notifications;
    public ProjectController(ProjectRepository projects, UserAccountRepository users, kz.studhub.chat.ChatService chat,
                             ProjectTaskRepository tasks, ProjectHistory history, NotificationService notifications) {
        this.history = history;
        this.projects = projects;
        this.users = users;
        this.chat = chat;
        this.tasks = tasks;
        this.notifications = notifications;
    }
    @PostMapping("/{id}/chat") @Transactional
    public kz.studhub.chat.ChatService.Room openChat(Authentication auth, @PathVariable Long id) {
        Long viewer=userId(auth);
        Project p=projects.locked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!p.ownerId.equals(viewer) && !p.memberIds.contains(viewer)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return chat.projectRoom(p.id,p.title,p.ownerId,p.memberIds,viewer,true);
    }
    public record Create(@NotBlank @Size(max=120) String title, @Size(max=1000) String description) {}
    public record Member(@NotNull @Positive Long userId) {}
    private Long userId(Authentication auth) { return users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId(); }
    private Map<String,Object> view(Project p, Long viewer) {
        var names = new HashMap<Long,String>();
        users.findChatIdentities(p.memberIds).forEach(u -> names.put(u.getId(), u.getId().equals(viewer) || "members".equals(u.getProfileVisibility()) ? (u.getNickname() == null || u.getNickname().isBlank() ? u.getFullName() : u.getNickname()) : "Участник"));
        return Map.of("closed",p.isClosed(),"pendingInvitations",p.ownerId.equals(viewer)?p.invitedIds.stream().sorted().toList():List.of(),"id",p.id,"title",p.title,"description",p.description,"owner",p.ownerId.equals(viewer),"ownerId",p.ownerId,"viewerId",viewer,"members",p.memberIds.stream().sorted().map(id -> Map.of("id",id,"name",names.getOrDefault(id,"Участник"))).toList());
    }
    public record Lifecycle(@NotNull Boolean closed) {}
    @PatchMapping("/{id}/state") @Transactional
    public Map<String,Object> state(Authentication auth,@PathVariable Long id,@Valid @RequestBody Lifecycle input) {
        Project p=owned(auth,id);
        if(p.isClosed()!=input.closed()) {p.closed=input.closed();history.record(id,auth,p.isClosed()?"Проект закрыт":"Проект открыт повторно");}
        return view(p,p.ownerId);
    }
    @GetMapping("/{id}/history") @Transactional(readOnly=true)
    public List<ProjectHistory.Entry> history(Authentication auth,@PathVariable Long id,@RequestParam(required=false) Long before) {
        detail(auth,id);return history.list(id,before);
    }
    @GetMapping("/invitations") @Transactional(readOnly=true)
    public List<Map<String,Object>> invitations(Authentication auth) {
        return projects.invitations(userId(auth)).stream().map(p -> Map.<String,Object>of("id",p.id,"title",p.title,"ownerId",p.ownerId,"closed",p.isClosed())).toList();
    }
    @PostMapping("/{id}/invitation/accept") @Transactional
    public Map<String,Object> accept(Authentication auth,@PathVariable Long id) {
        Long viewer=userId(auth);Project p=projects.locked(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!p.invitedIds.contains(viewer)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        p.requireOpen();
        if(p.memberIds.size()>=30) throw new ResponseStatusException(HttpStatus.CONFLICT,"Проект заполнен");
        p.invitedIds.remove(viewer);p.memberIds.add(viewer);
        chat.projectRoom(p.id,p.title,p.ownerId,p.memberIds,viewer,false);
        history.record(id,auth,"Приглашение принято; участник присоединился");return view(p,viewer);
    }
    @PostMapping("/{id}/invitation/decline") @Transactional
    public Map<String,Boolean> decline(Authentication auth,@PathVariable Long id) {
        Long viewer=userId(auth);Project p=projects.locked(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!p.invitedIds.remove(viewer)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        history.record(id,auth,"Приглашение отклонено");return Map.of("declined",true);
    }
    @DeleteMapping("/{id}/invitations/{userId}") @Transactional
    public Map<String,Boolean> cancelInvitation(Authentication auth,@PathVariable Long id,@PathVariable Long userId) {
        Project p=owned(auth,id);p.requireOpen();
        if(!p.invitedIds.remove(userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        history.record(id,auth,"Отменено приглашение SH-"+userId);return Map.of("cancelled",true);
    }
    @GetMapping @Transactional(readOnly=true)
    public List<Map<String,Object>> list(Authentication auth) { Long viewer=userId(auth); return projects.visible(viewer).stream().map(p -> view(p,viewer)).toList(); }
    @GetMapping("/{id}") @Transactional(readOnly=true)
    public Map<String,Object> detail(Authentication auth, @PathVariable Long id) {
        Long viewer=userId(auth);
        Project project=projects.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!project.ownerId.equals(viewer) && !project.memberIds.contains(viewer)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return view(project,viewer);
    }
    @PostMapping @Transactional
    public Map<String,Object> create(Authentication auth, @Valid @RequestBody Create input) {
        String title=input.title().trim(); if(title.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        Project p=new Project(); p.ownerId=userId(auth);
        p.title=title; p.description=input.description()==null ? "" : input.description().trim(); p.memberIds.add(p.ownerId);
        projects.saveAndFlush(p); history.record(p.id,auth,"Проект создан"); return view(p,p.ownerId);
    }
    private Project owned(Authentication auth, Long id) {
        Project p=projects.locked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!p.ownerId.equals(userId(auth))) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return p;
    }
    @PutMapping("/{id}") @Transactional
    public Map<String,Object> edit(Authentication auth, @PathVariable Long id, @Valid @RequestBody Create input) {
        Project p=owned(auth,id); p.requireOpen();
        String title=input.title().strip();
        if(title.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        history.record(id,auth,"Название или описание проекта изменены");
        p.title=title; p.description=input.description()==null ? "" : input.description().strip();
        chat.projectRoom(p.id,p.title,p.ownerId,p.memberIds,p.ownerId,false);
        return view(p,p.ownerId);
    }
    @DeleteMapping("/{id}/members/{memberId}") @Transactional
    public Map<String,Object> removeMember(Authentication auth, @PathVariable Long id, @PathVariable Long memberId) {
        Project p=owned(auth,id); p.requireOpen();
        if(p.ownerId.equals(memberId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Нельзя удалить владельца проекта");
        if(!p.memberIds.remove(memberId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        history.record(id,auth,"Удалён участник SH-"+memberId);
        tasks.findByProjectIdOrderByIdDesc(id).stream().filter(t -> memberId.equals(t.assigneeId)).forEach(t -> t.assigneeId=null);
        chat.projectRoom(p.id,p.title,p.ownerId,p.memberIds,p.ownerId,false);
        return view(p,p.ownerId);
    }
    @PostMapping("/{id}/members") @Transactional
    public Map<String,Object> add(Authentication auth, @PathVariable Long id, @Valid @RequestBody Member input) {
        Long viewer=userId(auth); Project p=projects.locked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!p.ownerId.equals(viewer)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        p.requireOpen();
        var person=users.findById(input.userId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!person.getId().equals(viewer) && !"members".equals(person.getProfileVisibility())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(p.memberIds.contains(input.userId()) || p.invitedIds.contains(input.userId())) return view(p,viewer);
        if(p.memberIds.size()+p.invitedIds.size()>=30) throw new ResponseStatusException(HttpStatus.CONFLICT,"Лимит участников и приглашений — 30");
        p.invitedIds.add(input.userId());
        history.record(id,auth,"Отправлено приглашение SH-"+input.userId());
        notifications.publish(input.userId(), NotificationType.PROJECT_INVITATION,
            "project:" + id + ":invitation:" + UUID.randomUUID(), "", "/projects");
        return view(p,viewer);
    }
}
