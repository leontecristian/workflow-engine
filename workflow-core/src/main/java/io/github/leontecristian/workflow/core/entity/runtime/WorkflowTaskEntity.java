package io.github.leontecristian.workflow.core.entity.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.TaskClosureReason;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_kind", nullable = false, length = 5)
    private AssignmentTargetKind assignmentKind;

    @Nationalized
    @Column(name = "assignment_ref", nullable = false, length = 200)
    private String assignmentRef;

    @Nationalized
    @Column(name = "assignee_ref", length = 200)
    private String assigneeRef;

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

    public WorkflowTaskEntity(long activityId, String taskKey, TaskAssignmentTarget assignment, LocalDateTime time) {
        if (activityId <= 0) throw new IllegalArgumentException("Invalid activity identity");
        this.activityId = activityId; this.taskKey = java.util.Objects.requireNonNull(taskKey);
        this.assignmentKind = java.util.Objects.requireNonNull(assignment.kind());
        this.assignmentRef = java.util.Objects.requireNonNull(assignment.ref());
        this.assigneeRef = assignmentKind == AssignmentTargetKind.USER ? assignmentRef : null;
        this.createdAt = java.util.Objects.requireNonNull(time); this.status = TaskStatus.OPEN;
    }

    public TaskAssignmentTarget getAssignment() {
        return new TaskAssignmentTarget(assignmentKind, assignmentRef);
    }

    public void claim(String actor) {
        if (status != TaskStatus.OPEN || assignmentKind == AssignmentTargetKind.USER || assigneeRef != null)
            throw new IllegalStateException("Unassigned OPEN shared task required");
        assigneeRef = java.util.Objects.requireNonNull(actor);
    }

    public void release() {
        if (status != TaskStatus.OPEN || assignmentKind == AssignmentTargetKind.USER || assigneeRef == null)
            throw new IllegalStateException("Assigned OPEN shared task required");
        assigneeRef = null;
    }

    public void cancelForResolution(LocalDateTime time, String actor) {
        if (status != TaskStatus.OPEN || time.isBefore(createdAt))
            throw new IllegalStateException("OPEN task required");
        status = TaskStatus.CANCELLED; closedAt = time; closedBy = java.util.Objects.requireNonNull(actor);
        closureReason = TaskClosureReason.ACTIVITY_RESOLVED;
    }

    public void complete(String outcome, LocalDateTime time, String actor) {
        if (status != TaskStatus.OPEN || time.isBefore(createdAt) || (assigneeRef != null && !assigneeRef.equals(actor)))
            throw new IllegalStateException("Assigned OPEN task required");
        status = TaskStatus.COMPLETED; outcomeKey = java.util.Objects.requireNonNull(outcome);
        closedAt = time; closedBy = java.util.Objects.requireNonNull(actor); closureReason = TaskClosureReason.TASK_OUTCOME;
    }

}
