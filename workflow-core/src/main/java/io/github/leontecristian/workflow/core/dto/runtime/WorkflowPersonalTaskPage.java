package io.github.leontecristian.workflow.core.dto.runtime;

import java.util.List;

/** The next position may be present even when no tasks were visible in this scan. */
public record WorkflowPersonalTaskPage(List<WorkflowTaskView> items, WorkflowPersonalTaskCursor next) {
    public WorkflowPersonalTaskPage {
        items = List.copyOf(items);
    }
}
