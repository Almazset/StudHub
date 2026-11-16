package kz.studhub.project;
import java.time.Instant;
import java.util.List;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;

@Service
public class ProjectHistory {
    private final ProjectEventRepository events;
    private final UserAccountRepository users;
    public ProjectHistory(ProjectEventRepository events,UserAccountRepository users) {this.events=events;this.users=users;}
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void record(Long projectId,Authentication auth,String description) {
        var event=new ProjectEvent(); event.projectId=projectId;event.actorId=users.findByEmailIgnoreCase(auth.getName()).orElseThrow().getId();
        event.occurredAt=Instant.now();event.description=description;events.save(event);
    }
    public record Entry(Long id,Long actorId,Instant occurredAt,String description) {}
    @Transactional(readOnly=true)
    public List<Entry> list(Long projectId,Long before) {
        return events.findTop50ByProjectIdAndIdLessThanOrderByIdDesc(projectId,before==null?Long.MAX_VALUE:before).stream()
            .map(e->new Entry(e.id,e.actorId,e.occurredAt,e.description)).toList();
    }
}
