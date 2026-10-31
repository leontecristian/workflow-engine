package io.github.leontecristian.workflow.core.entity.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.FractionalSeconds;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

/** Internal insert-only runtime evidence; no supported host mutation or deletion API. */
@Entity
@Table(name = "workflow_history", schema = "wf")
@Getter
public class WorkflowHistoryEntity {

    @EmbeddedId
    private WorkflowHistoryId id;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "command_id", nullable = false)
    private UUID commandId;

    @Nationalized
    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_kind", nullable = false, length = 12)
    private ActorKind actorKind;

    @Nationalized
    @Column(name = "actor_ref", nullable = false, length = 200)
    private String actorRef;

    /** Stored UTC time, without JVM-default-zone conversion. */
    @FractionalSeconds(7)
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "activity_id")
    private Long activityId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "source_node_key", length = 100)
    private String sourceNodeKey;

    @Column(name = "target_node_key", length = 100)
    private String targetNodeKey;

    @Column(name = "transition_key", length = 100)
    private String transitionKey;

    @Column(name = "details_schema_version", nullable = false)
    private Short detailsSchemaVersion;

    @Nationalized
    @JdbcTypeCode(SqlTypes.NVARCHAR)
    @Column(name = "details_json", nullable = false, columnDefinition = "nvarchar(max)")
    private String detailsJson;

    protected WorkflowHistoryEntity() {
    }

    /** Insert-only fact; there is no history mutation or deletion API. */
    public WorkflowHistoryEntity(io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary fact, String details) {
        this.id = new WorkflowHistoryId(fact.instanceId(), fact.historySequence());
        this.eventType = java.util.Objects.requireNonNull(fact.eventType());
        this.commandId = java.util.Objects.requireNonNull(fact.commandId()); this.correlationId = fact.correlationId();
        this.actorKind = java.util.Objects.requireNonNull(fact.actorKind()); this.actorRef = java.util.Objects.requireNonNull(fact.actorRef());
        this.occurredAt = LocalDateTime.ofInstant(fact.occurredAt(), java.time.ZoneOffset.UTC);
        this.activityId = fact.activityId(); this.taskId = fact.taskId();
        this.sourceNodeKey = fact.sourceNodeKey(); this.targetNodeKey = fact.targetNodeKey(); this.transitionKey = fact.transitionKey();
        this.detailsSchemaVersion = fact.detailsSchemaVersion(); this.detailsJson = java.util.Objects.requireNonNull(details);
    }

}
