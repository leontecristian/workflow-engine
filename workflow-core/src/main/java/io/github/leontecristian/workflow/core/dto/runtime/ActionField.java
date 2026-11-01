package io.github.leontecristian.workflow.core.dto.runtime;
public record ActionField(String key, VariableValueType type, boolean required, boolean nullable) {
    public ActionField {
        if (key==null || !key.matches("[A-Za-z][A-Za-z0-9._-]{0,99}") || type==null || type==VariableValueType.JSON)
            throw new IllegalArgumentException("Invalid action field");
    }
}
