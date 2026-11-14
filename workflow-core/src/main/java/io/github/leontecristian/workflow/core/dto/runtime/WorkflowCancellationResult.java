package io.github.leontecristian.workflow.core.dto.runtime;

import java.util.UUID;

/** Frozen cancellation evidence, provisional until the host transaction commits. */
public record WorkflowCancellationResult(UUID commandId, WorkflowOperation operation,
        WorkflowInstanceView instance, WorkflowActivityView activity, int cancelledTaskCount,
        long firstHistorySequence, long lastHistorySequence) { }
