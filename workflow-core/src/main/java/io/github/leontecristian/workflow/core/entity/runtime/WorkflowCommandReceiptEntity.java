package io.github.leontecristian.workflow.core.entity.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import org.hibernate.annotations.FractionalSeconds;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

/** Internal persistence state; not a supported host API. No mutation contract is provided. */
@Entity
@Table(name = "workflow_command_receipt", schema = "wf")
@Getter
public class WorkflowCommandReceiptEntity {

    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "command_id", nullable = false)
    private UUID commandId;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "operation_type", nullable = false, length = 40)
    private String operationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_kind", nullable = false, length = 12)
    private ActorKind actorKind;

    @Nationalized
    @Column(name = "actor_ref", nullable = false, length = 200)
    private String actorRef;

    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "request_fingerprint", nullable = false, length = 32)
    private byte[] requestFingerprint;

    @Column(name = "fingerprint_version", nullable = false, length = 40)
    private String fingerprintVersion;

    /** Stored UTC time, without JVM-default-zone conversion. */
    @FractionalSeconds(7)
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "result_schema_version", nullable = false)
    private Short resultSchemaVersion;

    @Nationalized
    @JdbcTypeCode(SqlTypes.LONG32NVARCHAR)
    @Column(name = "result_json", nullable = false, columnDefinition = "nvarchar(max)")
    private String resultJson;

    protected WorkflowCommandReceiptEntity() {
    }

    public byte[] getRequestFingerprint() {
        return requestFingerprint == null ? null : requestFingerprint.clone();
    }
}
