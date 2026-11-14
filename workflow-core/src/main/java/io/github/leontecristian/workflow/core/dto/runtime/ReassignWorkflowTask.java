package io.github.leontecristian.workflow.core.dto.runtime;
/** Exact target replacement, provisional until host transaction commit. */
public record ReassignWorkflowTask(java.util.UUID commandId,long instanceId,long taskId,long expectedLockVersion,TaskAssignmentTarget assignment,String reason) {}
