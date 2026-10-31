package io.github.leontecristian.workflow.core.dto.definition.document;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
/** Internal core-to-core pinned definition projection; not a host editing API. */
public record ExecutableDefinition(long versionId, long definitionId, String key, int version,
        String contentHash, DefinitionStatus status, MinimalWorkflowDefinition model) { }
