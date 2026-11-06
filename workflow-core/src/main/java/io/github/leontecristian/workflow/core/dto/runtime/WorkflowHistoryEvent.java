package io.github.leontecristian.workflow.core.dto.runtime;

/** Detached runtime contract. Host authorization and transaction completion remain required. */
public record WorkflowHistoryEvent(
        WorkflowHistorySummary summary,
        String detailsJson) {
}
