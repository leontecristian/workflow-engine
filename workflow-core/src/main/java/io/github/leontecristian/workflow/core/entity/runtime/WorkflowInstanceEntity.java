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

/** Internal persistence state; not a supported host API. No mutation contract is provided. */
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

}
