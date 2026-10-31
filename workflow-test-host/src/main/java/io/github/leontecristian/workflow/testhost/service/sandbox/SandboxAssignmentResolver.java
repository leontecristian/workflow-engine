package io.github.leontecristian.workflow.testhost.service.sandbox;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentContext;
import io.github.leontecristian.workflow.core.dto.runtime.ResolvedTask;
import io.github.leontecristian.workflow.core.dto.runtime.SingleAssignmentResult;
import io.github.leontecristian.workflow.core.service.runtime.SingleTaskAssignmentResolver;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;

import java.util.List;

public class SandboxAssignmentResolver implements SingleTaskAssignmentResolver {
    private final SandboxRequestStore requests;
    private final ConsoleSettings settings;

    public SandboxAssignmentResolver(SandboxRequestStore requests, ConsoleSettings settings) {
        this.requests = requests;
        this.settings = settings;
    }

    @Override
    public String identifier() {
        return "sandbox.singleReviewer";
    }

    @Override
    public SingleAssignmentResult resolve(AssignmentContext context) {
        if (!"SANDBOX_REQUEST".equals(context.businessType())
                || !requests.linked(
                        ConsoleWire.decimal(context.businessId(), false), context.instanceId()))
            throw new IllegalStateException("Sandbox link not established");
        return new SingleAssignmentResult(
                List.of(new ResolvedTask("REVIEW", settings.reviewer)), null);
    }
}
