package io.github.leontecristian.workflow.core.dto.runtime;

/** Detached exact-task context for a current-membership lookup. */
public record TaskEligibilityContext(WorkflowTaskView task, String actorRef) { }
