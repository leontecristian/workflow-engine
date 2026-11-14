package io.github.leontecristian.workflow.core.dto.runtime;

public record WorkflowCancellationAuthorizationContext(long instanceId, long definitionVersionId,
        String businessType, String businessId, WorkflowCommandContext commandContext) { }
