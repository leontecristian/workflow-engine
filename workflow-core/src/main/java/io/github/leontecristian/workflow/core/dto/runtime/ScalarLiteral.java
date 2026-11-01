package io.github.leontecristian.workflow.core.dto.runtime;
/** Exact transport token; absence is an omitted map key. */
public sealed interface ScalarLiteral permits TextLiteral, BooleanLiteral, NullLiteral {}
