package io.github.leontecristian.workflow.core.dto.runtime;

/** Exact host identity; validated at the execution boundary. */
public record TaskAssignmentTarget(AssignmentTargetKind kind, String ref) { }
