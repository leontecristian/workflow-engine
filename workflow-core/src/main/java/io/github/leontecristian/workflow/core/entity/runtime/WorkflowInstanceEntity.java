package io.github.leontecristian.workflow.core.entity.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import org.hibernate.annotations.FractionalSeconds;
import org.hibernate.annotations.Nationalized;

/** Internal runtime persistence; controlled changes belong to the host-coordinated execution service. */
@Entity
@Table(name = "workflow_instance", schema = "wf")
@Getter
public class WorkflowInstanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "definition_version_id", nullable = false)
    private Long definitionVersionId;

    @Column(name = "business_type", length = 100)
    private String businessType;

    @Nationalized
    @Column(name = "business_id", length = 200)
    private String businessId;

    @Column(name = "current_node_key", nullable = false, length = 100)
    private String currentNodeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private InstanceStatus status;

    @Column(name = "result_key", length = 100)
    private String resultKey;

    /** Stored UTC time, without JVM-default-zone conversion. */
    @FractionalSeconds(7)
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Nationalized
    @Column(name = "started_by", nullable = false, length = 200)
    private String startedBy;

    /** Stored UTC time, without JVM-default-zone conversion. */
    @FractionalSeconds(7)
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Nationalized
    @Column(name = "ended_by", length = 200)
    private String endedBy;

    @Nationalized
    @Column(name = "termination_reason", length = 2000)
    private String terminationReason;

    @Column(name = "last_history_sequence", nullable = false)
    private Long lastHistorySequence;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    protected WorkflowInstanceEntity() {
    }

    public WorkflowInstanceEntity(long definitionVersionId, String businessType, String businessId,
                                  String startKey, LocalDateTime time, String actor) {
        if (definitionVersionId <= 0 || (businessType == null) != (businessId == null)) throw new IllegalArgumentException("Invalid instance identity");
        this.definitionVersionId = definitionVersionId;
        this.businessType = businessType; this.businessId = businessId;
        this.currentNodeKey = java.util.Objects.requireNonNull(startKey);
        this.status = InstanceStatus.ACTIVE; this.lastHistorySequence = 0L;
        this.startedAt = java.util.Objects.requireNonNull(time); this.startedBy = java.util.Objects.requireNonNull(actor);
    }

    /** A real dirty managed write, flushed by the command before any child mutation or host effect. */
    public long reserveHistorySequence() {
        if (status != InstanceStatus.ACTIVE) throw new IllegalStateException("Active instance required");
        lastHistorySequence = Math.addExact(lastHistorySequence, 1);
        return lastHistorySequence;
    }

    public void waitAt(String humanKey, long finalSequence) {
        if (status != InstanceStatus.ACTIVE || lastHistorySequence != 0 || finalSequence < 4)
            throw new IllegalStateException("New SINGLE instance required");
        currentNodeKey = java.util.Objects.requireNonNull(humanKey); lastHistorySequence = finalSequence;
    }

    /** Continue an already claimed instance after atomically closing its previous activity. */
    public void advanceToHuman(String humanKey, long finalSequence) {
        if (status != InstanceStatus.ACTIVE || lastHistorySequence < 5 || finalSequence <= lastHistorySequence
                || resultKey != null || endedAt != null)
            throw new IllegalStateException("Claimed active instance required");
        currentNodeKey = java.util.Objects.requireNonNull(humanKey); lastHistorySequence = finalSequence;
    }

    public void complete(String endKey, String result, long finalSequence, LocalDateTime time, String actor) {
        if (status != InstanceStatus.ACTIVE || finalSequence <= lastHistorySequence || time.isBefore(startedAt))
            throw new IllegalStateException("Invalid completion");
        currentNodeKey = java.util.Objects.requireNonNull(endKey); resultKey = java.util.Objects.requireNonNull(result);
        status = InstanceStatus.COMPLETED; endedAt = time; endedBy = java.util.Objects.requireNonNull(actor);
        lastHistorySequence = finalSequence;
    }

}
