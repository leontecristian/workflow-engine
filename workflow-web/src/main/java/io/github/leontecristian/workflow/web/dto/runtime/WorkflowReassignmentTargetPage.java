package io.github.leontecristian.workflow.web.dto.runtime;

import java.util.List;

/** Bounded, host-owned directory cursor; it is unrelated to task-ID pagination. */
public record WorkflowReassignmentTargetPage(List<WorkflowReassignmentTarget> items, String nextCursor) {
    public WorkflowReassignmentTargetPage { items = List.copyOf(items); }
}
