package io.github.leontecristian.workflow.core.dto.runtime;

/**
 * Supply exactly one of workflowKey, definitionId (family), or definitionVersionId.
 * Family selection follows receipt lookup. Hosts own authorization and transaction completion.
 */
public record StartWorkflow(
        java.util.UUID commandId,
        String workflowKey,
        Long definitionId,
        Long definitionVersionId,
        String businessType,
        String businessId,
        HostStartIntent hostStartIntent) {
    /** Retained exact-version construction. */
    public StartWorkflow(java.util.UUID commandId, long definitionVersionId, String businessType,
            String businessId, HostStartIntent hostStartIntent) {
        this(commandId, null, null, definitionVersionId, businessType, businessId, hostStartIntent);
    }
}
