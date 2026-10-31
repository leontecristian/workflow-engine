package io.github.leontecristian.workflow.core.dto.runtime;

/** Detached runtime contract. Host authorization and transaction completion remain required. */
public record AssignmentContext(
        java.util.UUID commandId,
        long instanceId,
        long definitionVersionId,
        String definitionKey,
        int definitionVersion,
        String definitionHash,
        String humanNodeKey,
        String businessType,
        String businessId,
        WorkflowCommandContext context) {
}
