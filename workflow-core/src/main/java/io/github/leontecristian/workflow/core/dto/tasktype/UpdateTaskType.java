package io.github.leontecristian.workflow.core.dto.tasktype;

/** Replaces all mutable metadata; a null description clears the current description. */
public record UpdateTaskType(long id, long expectedVersion, String name, String description,
                             boolean selectable) {
}
