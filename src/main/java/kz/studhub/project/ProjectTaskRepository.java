package kz.studhub.project;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface ProjectTaskRepository extends JpaRepository<ProjectTask,Long> {
 List<ProjectTask> findByProjectIdOrderByIdDesc(Long projectId);
 List<ProjectTask> findByProjectIdInOrderByIdDesc(java.util.Collection<Long> projectIds);
 Optional<ProjectTask> findByIdAndProjectId(Long id, Long projectId);

 interface CalendarTask {
  Long getId();
  String getTitle();
  LocalDate getDueDate();
  String getStatus();
  Long getProjectId();
  String getProjectTitle();
 }

 String CALENDAR_TASKS = """
    select t.id as id, t.title as title, t.dueDate as dueDate, t.status as status,
           p.id as projectId, p.title as projectTitle
    from ProjectTask t join Project p on p.id = t.projectId
    where t.assigneeId = :viewerId and (p.ownerId = :viewerId or :viewerId member of p.memberIds)
      and (p.closed = false or p.closed is null)
    """;

 @Query(CALENDAR_TASKS + """
     and t.dueDate between :from and :to
     """)
 List<CalendarTask> calendarTasks(Long viewerId, LocalDate from, LocalDate to);

 @Query(CALENDAR_TASKS + """
     and t.dueDate <= :through and t.status <> 'DONE'
     order by case when t.dueDate = :today then 0 when t.dueDate < :today then 1 else 2 end,
              case when t.dueDate < :today then t.dueDate else null end desc, t.dueDate, t.id
     """)
 List<CalendarTask> calendarFocus(Long viewerId, LocalDate today, LocalDate through, Pageable limit);
}
