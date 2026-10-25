package io.github.leontecristian.workflow.core.dto.tasktype;

import java.util.Objects;
import java.util.Optional;

/** Provisional audit evidence until the enclosing host transaction commits. */
public record TaskTypeChange(Optional<TaskTypeView> before, TaskTypeView after, boolean changed) {

    public TaskTypeChange {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
    }
}
