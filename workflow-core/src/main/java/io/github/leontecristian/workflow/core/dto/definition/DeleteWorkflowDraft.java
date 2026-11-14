package io.github.leontecristian.workflow.core.dto.definition;

/** Permanently remove draft content while retaining its allocated identity. */
public record DeleteWorkflowDraft(long versionId, long expectedLockVersion) { }
