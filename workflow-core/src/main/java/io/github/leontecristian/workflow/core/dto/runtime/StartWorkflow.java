package io.github.leontecristian.workflow.core.dto.runtime;

/** Detached runtime contract. Host authorization and transaction completion remain required. */
public record StartWorkflow(
        java.util.UUID commandId,
        long definitionVersionId,
        String businessType,
        String businessId,
        HostStartIntent hostStartIntent) {
}
