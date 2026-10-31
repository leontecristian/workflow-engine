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

/** Internal version persistence state; controlled mutations require the administration transaction. */
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

    public WorkflowDefinitionVersionEntity(Long definitionId, int version, String document, byte[] hash,
                                           String algorithm, LocalDateTime time, String actor) {
        if (definitionId == null || definitionId <= 0 || version <= 0) throw new IllegalArgumentException("Invalid definition identity");
        this.definitionId = definitionId;
        this.version = version;
        this.schemaVersion = 1;
        this.status = DefinitionStatus.DRAFT;
        this.contentRevision = 1L;
        this.createdAt = java.util.Objects.requireNonNull(time);
        this.createdBy = java.util.Objects.requireNonNull(actor);
        content(document, hash, algorithm, time, actor);
    }

    private void content(String document, byte[] hash, String algorithm, LocalDateTime time, String actor) {
        if (hash == null || hash.length != 32) throw new IllegalArgumentException("Expected SHA-256 hash");
        this.definitionJson = java.util.Objects.requireNonNull(document);
        this.contentHash = hash.clone();
        this.canonicalizationVersion = java.util.Objects.requireNonNull(algorithm);
        this.updatedAt = java.util.Objects.requireNonNull(time);
        this.updatedBy = java.util.Objects.requireNonNull(actor);
    }

    public void replaceDraft(String document, byte[] hash, String algorithm, LocalDateTime time, String actor) {
        requireDraft();
        content(document, hash, algorithm, time, actor);
        this.contentRevision = Math.addExact(contentRevision, 1);
        this.validationStatus = null;
        this.validatedContentRevision = null;
        this.validatedContentHash = null;
        this.validationContextHash = null;
        this.validationDetailsJson = null;
        this.validatedAt = null;
        this.validatedBy = null;
    }

    public void recordValidation(boolean valid, byte[] contextHash, String details, LocalDateTime time, String actor) {
        requireDraft();
        if (contextHash == null || contextHash.length != 32) throw new IllegalArgumentException("Expected context hash");
        this.validationStatus = valid ? DefinitionValidationStatus.PASSED : DefinitionValidationStatus.FAILED;
        this.validatedContentRevision = contentRevision;
        this.validatedContentHash = contentHash.clone();
        this.validationContextHash = contextHash.clone();
        this.validationDetailsJson = java.util.Objects.requireNonNull(details);
        this.validatedAt = java.util.Objects.requireNonNull(time);
        this.validatedBy = java.util.Objects.requireNonNull(actor);
        this.updatedAt = time;
        this.updatedBy = actor;
    }

    public void publish(LocalDateTime time, String actor) {
        requireDraft();
        if (validationStatus != DefinitionValidationStatus.PASSED
                || !contentRevision.equals(validatedContentRevision)
                || !java.util.Arrays.equals(contentHash, validatedContentHash)) throw new IllegalStateException("Current validation required");
        this.status = DefinitionStatus.PUBLISHED;
        this.publishedAt = java.util.Objects.requireNonNull(time);
        this.publishedBy = java.util.Objects.requireNonNull(actor);
        this.updatedAt = time;
        this.updatedBy = actor;
    }

    public void retire(LocalDateTime time, String actor) {
        if (status != DefinitionStatus.PUBLISHED) throw new IllegalStateException("Published version required");
        this.status = DefinitionStatus.RETIRED;
        this.retiredAt = java.util.Objects.requireNonNull(time);
        this.retiredBy = java.util.Objects.requireNonNull(actor);
        this.updatedAt = time;
        this.updatedBy = actor;
    }

    private void requireDraft() {
        if (status != DefinitionStatus.DRAFT) throw new IllegalStateException("Draft required");
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
