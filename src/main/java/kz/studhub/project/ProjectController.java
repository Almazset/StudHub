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
    public ProjectController(ProjectRepository projects, UserAccountRepository users) { this.projects=projects; this.users=users; }
    public record Create(@NotBlank @Size(max=120) String title, @Size(max=1000) String description) {}
    public record Member(@NotNull @Positive Long userId) {}
    private Long userId(Authentication auth) { return users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId(); }
    private Map<String,Object> view(Project p, Long viewer) {
        var names = new HashMap<Long,String>();
        users.findChatIdentities(p.memberIds).forEach(u -> names.put(u.getId(), u.getId().equals(viewer) || "members".equals(u.getProfileVisibility()) ? (u.getNickname() == null || u.getNickname().isBlank() ? u.getFullName() : u.getNickname()) : "Участник"));
        return Map.of("id",p.id,"title",p.title,"description",p.description,"owner",p.ownerId.equals(viewer),"members",p.memberIds.stream().sorted().map(id -> Map.of("id",id,"name",names.getOrDefault(id,"Участник"))).toList());
    }
    @GetMapping @Transactional(readOnly=true)
    public List<Map<String,Object>> list(Authentication auth) { Long viewer=userId(auth); return projects.visible(viewer).stream().map(p -> view(p,viewer)).toList(); }
    @PostMapping @Transactional
    public Map<String,Object> create(Authentication auth, @Valid @RequestBody Create input) {
        String title=input.title().trim(); if(title.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        Project p=new Project(); p.ownerId=userId(auth); p.title=title; p.description=input.description()==null ? "" : input.description().trim(); p.memberIds.add(p.ownerId);
        projects.saveAndFlush(p); return view(p,p.ownerId);
    }
    @PostMapping("/{id}/members") @Transactional
    public Map<String,Object> add(Authentication auth, @PathVariable Long id, @Valid @RequestBody Member input) {
        Long viewer=userId(auth); Project p=projects.locked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!p.ownerId.equals(viewer)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var person=users.findById(input.userId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!person.getId().equals(viewer) && !"members".equals(person.getProfileVisibility())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(p.memberIds.size()>=30 && !p.memberIds.contains(input.userId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        p.memberIds.add(input.userId()); projects.saveAndFlush(p); return view(p,viewer);
    }
}
