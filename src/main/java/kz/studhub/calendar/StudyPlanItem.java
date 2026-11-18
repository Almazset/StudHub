package kz.studhub.calendar;

import jakarta.persistence.*;
import java.time.LocalDate;
import kz.studhub.model.UserAccount;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "study_plan_items",
    indexes = @Index(name = "study_plan_owner_date", columnList = "owner_id, planned_date, id"))
public class StudyPlanItem {
    static final int TITLE_LENGTH = 150;
    static final int DESCRIPTION_LENGTH = 1000;
    static final int MIN_MINUTES = 5;
    static final int MAX_MINUTES = 720;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserAccount owner;

    @Column(nullable = false, length = TITLE_LENGTH)
    String title;

    @Column(name = "planned_date", nullable = false)
    LocalDate date;

    @Column(nullable = false)
    int minutes;

    @Column(nullable = false, length = DESCRIPTION_LENGTH)
    String description;

    @Column(nullable = false)
    boolean completed;

    protected StudyPlanItem() {}

    StudyPlanItem(UserAccount owner) {
        this.owner = owner;
    }
}
