package io.github.leontecristian.workflow.core.dto.runtime;

/** Detached exact-task and trusted actor context for read policy, eligibility and operation decisions. */
public record TaskEligibilityContext(WorkflowTaskView task, String actorRef) { }
