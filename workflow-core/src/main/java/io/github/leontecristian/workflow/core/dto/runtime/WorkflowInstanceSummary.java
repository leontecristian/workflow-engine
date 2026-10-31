package io.github.leontecristian.workflow.core.dto.runtime;

import java.time.Instant;

/** Detached indexed metadata, not a receipt snapshot or a full pinned-evidence integrity check. */
public record WorkflowInstanceSummary(
        long id,
        long definitionVersionId,
        String definitionKey,
        int definitionVersion,
        InstanceStatus status,
        String currentNodeKey,
        String resultKey,
        String businessType,
        String businessId,
        Instant startedAt,
        String startedBy,
        Instant endedAt,
        long lastHistorySequence,
        long lockVersion) {}
