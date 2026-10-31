package io.github.leontecristian.workflow.core.entity.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.TaskClosureReason;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import org.hibernate.annotations.FractionalSeconds;
import org.hibernate.annotations.Nationalized;

/** Internal runtime persistence; controlled changes belong to the host-coordinated execution service. */
@Entity
@Table(name = "workflow_task", schema = "wf")
@Getter
public class WorkflowTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "task_key", nullable = false, length = 100)
    private String taskKey;

    @Nationalized
    @Column(name = "assigned_user_ref", nullable = false, length = 200)
    private String assignedUserRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private TaskStatus status;

    @Column(name = "outcome_key", length = 100)
    private String outcomeKey;

    /** Stored UTC time, without JVM-default-zone conversion. */
    @FractionalSeconds(7)
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Stored UTC time, without JVM-default-zone conversion. */
    @FractionalSeconds(7)
    @Column(name = "due_at")
    private LocalDateTime dueAt;

    /** Stored UTC time, without JVM-default-zone conversion. */
    @FractionalSeconds(7)
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Nationalized
    @Column(name = "closed_by", length = 200)
    private String closedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "closure_reason", length = 40)
    private TaskClosureReason closureReason;

    protected WorkflowTaskEntity() {
    }

    public WorkflowTaskEntity(long activityId, String taskKey, String assignee, LocalDateTime time) {
        if (activityId <= 0) throw new IllegalArgumentException("Invalid activity identity");
        this.activityId = activityId; this.taskKey = java.util.Objects.requireNonNull(taskKey);
        this.assignedUserRef = java.util.Objects.requireNonNull(assignee);
        this.createdAt = java.util.Objects.requireNonNull(time); this.status = TaskStatus.OPEN;
    }

    public void complete(String outcome, LocalDateTime time, String actor) {
        if (status != TaskStatus.OPEN || time.isBefore(createdAt) || !assignedUserRef.equals(actor))
            throw new IllegalStateException("Assigned OPEN task required");
        status = TaskStatus.COMPLETED; outcomeKey = java.util.Objects.requireNonNull(outcome);
        closedAt = time; closedBy = actor; closureReason = TaskClosureReason.TASK_OUTCOME;
    }

}
