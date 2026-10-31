package io.github.leontecristian.workflow.core.dto.runtime;
import java.util.List;
public record WorkflowHistoryPage(List<WorkflowHistorySummary> items, Long nextAfterSequence) {
    public WorkflowHistoryPage { items = List.copyOf(items); }
}
