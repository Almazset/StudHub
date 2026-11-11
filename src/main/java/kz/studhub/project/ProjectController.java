package kz.studhub.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
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
    public ProjectController(ProjectRepository projects, UserAccountRepository users, kz.studhub.chat.ChatService chat, ProjectTaskRepository tasks) { this.projects=projects; this.users=users; this.chat=chat; this.tasks=tasks; }
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
        return Map.of("id",p.id,"title",p.title,"description",p.description,"owner",p.ownerId.equals(viewer),"ownerId",p.ownerId,"viewerId",viewer,"members",p.memberIds.stream().sorted().map(id -> Map.of("id",id,"name",names.getOrDefault(id,"Участник"))).toList());
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
        Project p=new Project(); p.ownerId=userId(auth); p.title=title; p.description=input.description()==null ? "" : input.description().trim(); p.memberIds.add(p.ownerId);
        projects.saveAndFlush(p); return view(p,p.ownerId);
    }
    private Project owned(Authentication auth, Long id) {
        Project p=projects.locked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!p.ownerId.equals(userId(auth))) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return p;
    }
    @PutMapping("/{id}") @Transactional
    public Map<String,Object> edit(Authentication auth, @PathVariable Long id, @Valid @RequestBody Create input) {
        Project p=owned(auth,id);
        String title=input.title().strip();
        if(title.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        p.title=title; p.description=input.description()==null ? "" : input.description().strip();
        chat.projectRoom(p.id,p.title,p.ownerId,p.memberIds,p.ownerId,false);
        return view(p,p.ownerId);
    }
    @DeleteMapping("/{id}/members/{memberId}") @Transactional
    public Map<String,Object> removeMember(Authentication auth, @PathVariable Long id, @PathVariable Long memberId) {
        Project p=owned(auth,id);
        if(p.ownerId.equals(memberId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Нельзя удалить владельца проекта");
        if(!p.memberIds.remove(memberId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        tasks.findByProjectIdOrderByIdDesc(id).stream().filter(t -> memberId.equals(t.assigneeId)).forEach(t -> t.assigneeId=null);
        chat.projectRoom(p.id,p.title,p.ownerId,p.memberIds,p.ownerId,false);
        return view(p,p.ownerId);
    }
    @PostMapping("/{id}/members") @Transactional
    public Map<String,Object> add(Authentication auth, @PathVariable Long id, @Valid @RequestBody Member input) {
        Long viewer=userId(auth); Project p=projects.locked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!p.ownerId.equals(viewer)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var person=users.findById(input.userId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!person.getId().equals(viewer) && !"members".equals(person.getProfileVisibility())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(p.memberIds.size()>=30 && !p.memberIds.contains(input.userId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        p.memberIds.add(input.userId()); projects.saveAndFlush(p);
        chat.projectRoom(p.id,p.title,p.ownerId,p.memberIds,viewer,false);
        return view(p,viewer);
    }
}
