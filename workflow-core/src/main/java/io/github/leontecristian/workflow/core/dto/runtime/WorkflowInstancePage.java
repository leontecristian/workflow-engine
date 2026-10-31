package io.github.leontecristian.workflow.core.dto.runtime;

import java.util.List;

/** Exclusive ascending ID continuation; no global total or stable snapshot is implied. */
public record WorkflowInstancePage(List<WorkflowInstanceSummary> items, Long nextAfterId) {
    public WorkflowInstancePage {
        items = List.copyOf(items);
    }
}
