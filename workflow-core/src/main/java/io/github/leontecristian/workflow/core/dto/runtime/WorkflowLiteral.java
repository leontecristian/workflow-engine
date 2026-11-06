package io.github.leontecristian.workflow.core.dto.runtime;
/** Exact transport token; absence is an omitted map key. */
public sealed interface WorkflowLiteral permits TextLiteral, BooleanLiteral, NullLiteral, WorkflowJsonValue {}
