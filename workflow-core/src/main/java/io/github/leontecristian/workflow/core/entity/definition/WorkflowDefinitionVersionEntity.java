package io.github.leontecristian.workflow.core.entity.definition;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionValidationStatus;
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
import lombok.AccessLevel;
import lombok.Getter;
import org.hibernate.annotations.FractionalSeconds;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

/** Internal persistence state; not a supported host API. No mutation contract is provided. */
@Entity
@Table(name = "workflow_definition_version", schema = "wf")
@Getter
public class WorkflowDefinitionVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "definition_id", nullable = false)
    private Long definitionId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "schema_version", nullable = false)
    private Short schemaVersion;

    @Column(name = "expression_policy", nullable = false, length = 100, insertable = false, updatable = false)
    private String legacyExpressionPolicy;

    @Nationalized
    @JdbcTypeCode(SqlTypes.LONG32NVARCHAR)
    @Column(name = "definition_json", nullable = false, columnDefinition = "nvarchar(max)")
    private String definitionJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private DefinitionStatus status;

    @Column(name = "content_revision", nullable = false)
    private Long contentRevision;

    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "content_hash", nullable = false, length = 32)
    private byte[] contentHash;

    @Column(name = "canonicalization_version", nullable = false, length = 40)
    private String canonicalizationVersion;

    /** Stored UTC time, without JVM-default-zone conversion. */
    @FractionalSeconds(7)
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Nationalized
    @Column(name = "created_by", nullable = false, length = 200)
    private String createdBy;

    /** Stored UTC time, without JVM-default-zone conversion. */
    @FractionalSeconds(7)
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Nationalized
    @Column(name = "updated_by", nullable = false, length = 200)
    private String updatedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", length = 12)
    private DefinitionValidationStatus validationStatus;

    @Column(name = "validated_content_revision")
    private Long validatedContentRevision;

    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "validated_content_hash", length = 32)
    private byte[] validatedContentHash;

    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "validation_context_hash", length = 32)
    private byte[] validationContextHash;

    @Nationalized
    @JdbcTypeCode(SqlTypes.LONG32NVARCHAR)
    @Column(name = "validation_details_json", columnDefinition = "nvarchar(max)")
    private String validationDetailsJson;

    /** Stored UTC time, without JVM-default-zone conversion. */
    @FractionalSeconds(7)
    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Nationalized
    @Column(name = "validated_by", length = 200)
    private String validatedBy;

    /** Stored UTC time, without JVM-default-zone conversion. */
    @FractionalSeconds(7)
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Nationalized
    @Column(name = "published_by", length = 200)
    private String publishedBy;

    /** Stored UTC time, without JVM-default-zone conversion. */
    @FractionalSeconds(7)
    @Column(name = "retired_at")
    private LocalDateTime retiredAt;

    @Nationalized
    @Column(name = "retired_by", length = 200)
    private String retiredBy;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    protected WorkflowDefinitionVersionEntity() {
    }

    public byte[] getContentHash() {
        return contentHash == null ? null : contentHash.clone();
    }

    public byte[] getValidatedContentHash() {
        return validatedContentHash == null ? null : validatedContentHash.clone();
    }

    public byte[] getValidationContextHash() {
        return validationContextHash == null ? null : validationContextHash.clone();
    }
}
