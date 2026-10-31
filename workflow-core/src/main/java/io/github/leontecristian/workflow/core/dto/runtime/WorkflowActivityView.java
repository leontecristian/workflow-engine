package io.github.leontecristian.workflow.core.dto.runtime;

/** Detached runtime contract. Host authorization and transaction completion remain required. */
public record WorkflowActivityView(
        long id,
        long instanceId,
        String nodeKey,
        long activationSequence,
        long taskTypeId,
        CompletionPolicy completionPolicy,
        String contributionOutcomeKey,
        int requiredTaskCount,
        String reviewRevisionRef,
        ActivityStatus status,
        String outcomeKey,
        java.time.Instant enteredAt,
        java.time.Instant closedAt,
        String closedBy,
        String closureReason) {
}
