package io.github.leontecristian.workflow.core.dto.definition;

/** Copy a saved version using its loaded technical token; numbering is server allocated. */
public record CreateWorkflowDraftFromVersion(long sourceVersionId, long expectedLockVersion) { }
