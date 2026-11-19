package io.github.leontecristian.workflow.core.dto.runtime;

import java.util.Objects;

/** A command authorization scope on the corresponding workflow resource. */
public record WorkflowPermission(WorkflowResource resource, WorkflowScope scope) {
    public WorkflowPermission {
        Objects.requireNonNull(resource, "resource");
        WorkflowResource expected = switch (Objects.requireNonNull(scope, "scope")) {
            case ACCESS_COMPLETION, COMPLETE, ACCESS_OWNERSHIP, ADMIN_RELEASE, ACCESS_REASSIGNMENT, REASSIGN ->
                    WorkflowResource.TASK;
            case ACCESS_CANCELLATION, CANCEL -> WorkflowResource.INSTANCE;
        };
        if (resource != expected) {
            throw new IllegalArgumentException("Scope does not apply to the specified workflow resource");
        }
    }
}
