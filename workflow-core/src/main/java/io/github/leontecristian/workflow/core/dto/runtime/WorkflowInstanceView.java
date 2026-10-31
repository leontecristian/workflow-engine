package io.github.leontecristian.workflow.core.dto.runtime;

/** Detached runtime contract. Host authorization and transaction completion remain required. */
public record WorkflowInstanceView(
        long id,
        long definitionVersionId,
        String definitionKey,
        int definitionVersion,
        String definitionHash,
        String businessType,
        String businessId,
        String currentNodeKey,
        InstanceStatus status,
        String resultKey,
        java.time.Instant startedAt,
        String startedBy,
        java.time.Instant endedAt,
        String endedBy,
        String terminationReason,
        long lastHistorySequence,
        long lockVersion) {
}
