package io.github.leontecristian.workflow.core.dto.runtime;
import java.util.List;
public record WorkflowTaskPage(List<WorkflowTaskView> items, Long nextAfterId) {
    public WorkflowTaskPage { items = List.copyOf(items); }
}
