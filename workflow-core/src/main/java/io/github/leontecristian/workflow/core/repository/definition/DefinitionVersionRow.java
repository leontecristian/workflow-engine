package io.github.leontecristian.workflow.core.repository.definition;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionValidationStatus;
import java.time.LocalDateTime;

/** Internal scalar summary projection: never selects definition or validation JSON payloads. */
public interface DefinitionVersionRow {
    Long getId();
    Long getDefinitionId();
    Integer getVersion();
    Short getSchemaVersion();
    DefinitionStatus getStatus();
    Long getContentRevision();
    Long getLockVersion();
    byte[] getContentHash();
    String getCanonicalizationVersion();
    LocalDateTime getCreatedAt();
    String getCreatedBy();
    LocalDateTime getUpdatedAt();
    String getUpdatedBy();
    LocalDateTime getPublishedAt();
    String getPublishedBy();
    LocalDateTime getRetiredAt();
    String getRetiredBy();
    DefinitionValidationStatus getValidationStatus();
    Long getValidatedContentRevision();
    byte[] getValidatedContentHash();
    byte[] getValidationContextHash();
    LocalDateTime getValidatedAt();
    String getValidatedBy();
}
