package io.github.leontecristian.workflow.core.dto.runtime;

/** Detached runtime contract. Host authorization and transaction completion remain required. */
public record AllocatedWorkflowInstance(
        java.util.UUID commandId,
        long instanceId,
        long definitionVersionId,
        String businessType,
        String businessId,
        WorkflowCommandContext context) {
}
