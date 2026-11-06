package io.github.leontecristian.workflow.core.dto.runtime;
public record WorkflowVariableView(String key, VariableValueType type, boolean initialized, WorkflowValue value) {}
