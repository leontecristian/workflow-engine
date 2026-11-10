package io.github.leontecristian.workflow.core.dto.runtime;

/** Exact identities and trusted context for a fresh, authorized completion. */
public record TaskSubmissionValidationContext(WorkflowCommandContext commandContext, long instanceId,
        long definitionVersionId, long activityId, long taskId, String nodeKey, String taskType,
        String outcomeKey, String comment) {}
