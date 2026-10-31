package io.github.leontecristian.workflow.core.dto.definition.document;
/** Internal read identity; current-state views do not require executable topology. */
public record PinnedDefinitionIdentity(long versionId, String key, int version, String contentHash) { }
