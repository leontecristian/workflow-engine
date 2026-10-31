package io.github.leontecristian.workflow.core.dto.definition;

/** Immutable in-process definition contract; the host owns authorization and transport mapping. */
public record ReplaceWorkflowDraft(long versionId, long expectedLockVersion, String documentJson) {
}
