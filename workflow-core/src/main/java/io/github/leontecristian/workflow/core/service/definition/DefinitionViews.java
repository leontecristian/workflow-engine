package io.github.leontecristian.workflow.core.service.definition;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionView;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionValidationSummary;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionSummary;
import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionEntity;
import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionVersionEntity;
import io.github.leontecristian.workflow.core.repository.definition.DefinitionVersionRow;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

final class DefinitionViews {
    private DefinitionViews() { }
    static Instant instant(LocalDateTime time) { return time == null ? null : time.toInstant(ZoneOffset.UTC); }
    static DefinitionView family(WorkflowDefinitionEntity e) {
        return new DefinitionView(e.getId(), e.getWorkflowKey(), e.getCatalogName(), e.getCatalogDescription(),
                instant(e.getCreatedAt()), e.getCreatedBy(), e.getLockVersion());
    }
    static DefinitionVersionSummary summary(String key, WorkflowDefinitionVersionEntity e) {
        return new DefinitionVersionSummary(e.getId(), e.getDefinitionId(), key, e.getVersion(), e.getSchemaVersion(),
                e.getStatus(), e.getContentRevision(), e.getLockVersion(), DefinitionContentCanonicalizer.hex(e.getContentHash()),
                e.getCanonicalizationVersion(), instant(e.getCreatedAt()), e.getCreatedBy(), instant(e.getUpdatedAt()), e.getUpdatedBy(),
                instant(e.getPublishedAt()), e.getPublishedBy(), instant(e.getRetiredAt()), e.getRetiredBy(),
                e.getValidationStatus() == null ? null : new DefinitionValidationSummary(e.getValidationStatus(),
                        e.getValidatedContentRevision(), DefinitionContentCanonicalizer.hex(e.getValidatedContentHash()),
                        DefinitionContentCanonicalizer.hex(e.getValidationContextHash()), instant(e.getValidatedAt()), e.getValidatedBy()));
    }
    static DefinitionVersionSummary summary(String key, DefinitionVersionRow e) {
        return new DefinitionVersionSummary(e.getId(), e.getDefinitionId(), key, e.getVersion(), e.getSchemaVersion(),
                e.getStatus(), e.getContentRevision(), e.getLockVersion(), DefinitionContentCanonicalizer.hex(e.getContentHash()),
                e.getCanonicalizationVersion(), instant(e.getCreatedAt()), e.getCreatedBy(), instant(e.getUpdatedAt()), e.getUpdatedBy(),
                instant(e.getPublishedAt()), e.getPublishedBy(), instant(e.getRetiredAt()), e.getRetiredBy(),
                e.getValidationStatus() == null ? null : new DefinitionValidationSummary(e.getValidationStatus(),
                        e.getValidatedContentRevision(), DefinitionContentCanonicalizer.hex(e.getValidatedContentHash()),
                        DefinitionContentCanonicalizer.hex(e.getValidationContextHash()), instant(e.getValidatedAt()), e.getValidatedBy()));
    }
}
