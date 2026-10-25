package io.github.leontecristian.workflow.core.dto.tasktype;

/** Creates a permanent code; text is validated and preserved by the catalog service. */
public record CreateTaskType(String code, String name, String description, boolean selectable) {
}
