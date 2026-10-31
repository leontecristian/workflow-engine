package io.github.leontecristian.workflow.core.entity.runtime;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.Getter;

/** Owner-aware internal persistence key; comparisons preserve case and content. */
@Embeddable
@Getter
public class WorkflowVariableId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final Pattern VARIABLE_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,99}");

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "variable_key", nullable = false, length = 100)
    private String variableKey;

    protected WorkflowVariableId() {
    }

    public WorkflowVariableId(Long instanceId, String variableKey) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(variableKey, "variableKey");
        if (instanceId <= 0) {
            throw new IllegalArgumentException("instanceId must be positive");
        }
        if (!VARIABLE_KEY.matcher(variableKey).matches()) {
            throw new IllegalArgumentException("Invalid variableKey");
        }
        this.instanceId = instanceId;
        this.variableKey = variableKey;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WorkflowVariableId that)) {
            return false;
        }
        return Objects.equals(instanceId, that.instanceId) && Objects.equals(variableKey, that.variableKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceId, variableKey);
    }
}
