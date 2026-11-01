package io.github.leontecristian.workflow.core.dto.runtime;
import java.util.UUID;
public record ServiceActionContext(UUID commandId, long instanceId, long definitionVersionId, String nodeKey,
        String businessType, String businessId, WorkflowCommandContext commandContext) {}
