package io.github.leontecristian.workflow.testhost.dto.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.StartWorkflow;
import io.github.leontecristian.workflow.testhost.service.sandbox.SandboxStartIdentity;

import java.util.UUID;

public record SandboxStart(
        UUID commandId, long definitionVersionId, long requestId, long expectedRequestVersion) {
    public StartWorkflow core() {
        return new StartWorkflow(
                commandId,
                definitionVersionId,
                "SANDBOX_REQUEST",
                Long.toString(requestId),
                SandboxStartIdentity.intent(requestId, expectedRequestVersion));
    }
}
