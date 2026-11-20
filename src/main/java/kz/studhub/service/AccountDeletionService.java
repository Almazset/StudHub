package kz.studhub.service;

import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDeletionService {
    private final UserAccountRepository users;
    private final NamedParameterJdbcTemplate jdbc;

    public enum Reason { SELF, LINKED_DATA }

    public static class DeletionBlocked extends RuntimeException {
        private final Reason reason;

        public DeletionBlocked(Reason reason) {
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }

    public AccountDeletionService(UserAccountRepository users, NamedParameterJdbcTemplate jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    @Transactional
    public String delete(Long id, String currentEmail) {
        UserAccount user = users.findLockedById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        if (user.getEmail().equalsIgnoreCase(currentEmail)) throw new DeletionBlocked(Reason.SELF);
        if (hasRelatedData(id)) throw new DeletionBlocked(Reason.LINKED_DATA);
        String email = user.getEmail();
        try {
            users.delete(user);
            users.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new DeletionBlocked(Reason.LINKED_DATA);
        }
        return email;
    }

    private boolean hasRelatedData(Long id) {
        // Both database foreign keys and application-managed user IDs must be checked.
        String sql = """
            select case when
                exists (select 1 from assignments where student_id = :id)
                or exists (select 1 from assistant_notes where owner_id = :id)
                or exists (select 1 from study_plan_items where owner_id = :id)
                or exists (select 1 from study_steps where user_id = :id)
                or exists (select 1 from lesson_progress where user_id = :id)
                or exists (select 1 from study_projects where owner_id = :id)
                or exists (select 1 from study_project_members where user_id = :id)
                or exists (select 1 from study_project_invitations where user_id = :id)
                or exists (select 1 from study_project_events where actor_id = :id)
                or exists (select 1 from project_tasks where assignee_id = :id)
                or exists (select 1 from chat_rooms where owner_id = :id)
                or exists (select 1 from chat_members where user_id = :id)
                or exists (select 1 from chat_messages where sender_id = :id)
                or exists (select 1 from chat_reactions where user_id = :id)
                or exists (select 1 from chat_favorites where user_id = :id)
                or exists (select 1 from friendships where low_user_id = :id or high_user_id = :id or requester_id = :id)
                or exists (select 1 from friend_presence_preferences where user_id = :id)
                or exists (select 1 from support_request where owner_id = :id)
                or exists (select 1 from user_notifications where recipient_id = :id)
                or exists (select 1 from notification_mutes where recipient_id = :id)
                or exists (select 1 from user_blocks where blocker_id = :id or blocked_id = :id)
                or exists (select 1 from user_reports where reporter_id = :id or reported_id = :id)
            then true else false end
            """;
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), Boolean.class));
    }
}
