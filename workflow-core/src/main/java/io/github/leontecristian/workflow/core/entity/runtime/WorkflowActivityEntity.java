package io.github.leontecristian.workflow.core.entity.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.ActivityStatus;
import io.github.leontecristian.workflow.core.dto.runtime.CompletionPolicy;
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

/** Internal persistence state; not a supported host API. No mutation contract is provided. */
@Entity
@Table(name = "workflow_activity", schema = "wf")
@Getter
public class WorkflowActivityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "node_key", nullable = false, length = 100)
    private String nodeKey;

    @Column(name = "activation_sequence", nullable = false)
    private Long activationSequence;

    @Column(name = "task_type_id", nullable = false)
    private Long taskTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_policy", nullable = false, length = 12)
    private CompletionPolicy completionPolicy;

    @Column(name = "contribution_outcome_key", length = 100)
    private String contributionOutcomeKey;

    @Column(name = "required_task_count", nullable = false)
    private Integer requiredTaskCount;

    @Nationalized
    @Column(name = "review_revision_ref", length = 200)
    private String reviewRevisionRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private ActivityStatus status;

    @Column(name = "outcome_key", length = 100)
    private String outcomeKey;

    /** Stored UTC time, without JVM-default-zone conversion. */
    @FractionalSeconds(7)
    @Column(name = "entered_at", nullable = false)
    private LocalDateTime enteredAt;

    /** Stored UTC time, without JVM-default-zone conversion. */
    @FractionalSeconds(7)
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Nationalized
    @Column(name = "closed_by", length = 200)
    private String closedBy;

    @Column(name = "closure_reason", length = 40)
    private String closureReason;

    protected WorkflowActivityEntity() {
    }

}
