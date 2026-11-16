package io.github.leontecristian.workflow.core.dto.runtime;

/** Detached verified target and pinned fresh-completion intent; supplied inside the host transaction. */
public record TaskCompletionContext(WorkflowCommandContext commandContext, WorkflowTaskView task,
        long definitionVersionId, String nodeKey, String taskType, String outcomeKey, String comment) { }
