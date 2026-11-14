package io.github.leontecristian.workflow.web.dto.runtime;

/** One host-approved directory choice for the exact receiving task. */
public record WorkflowReassignmentTarget(String ref, String label) { }
