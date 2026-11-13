package io.github.leontecristian.workflow.testhost.service.sandbox;

import io.github.leontecristian.workflow.core.dto.runtime.AllAssignmentResult;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentContext;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.ResolvedTask;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.service.runtime.AllTaskAssignmentResolver;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.runtime.SandboxSharedAssignmentProvider;
import java.util.List;
import java.util.Set;

/** Fixed mixed-target fixture; shared targets remain one task each. */
public class SandboxAllAssignmentResolver implements AllTaskAssignmentResolver {
    private final SandboxRequestStore requests;
    private final ConsoleSettings settings;

    public SandboxAllAssignmentResolver(SandboxRequestStore requests, ConsoleSettings settings) {
        this.requests = requests;
        this.settings = settings;
    }

    @Override
    public String identifier() {
        return "sandbox.allReviewers";
    }

    @Override
    public Set<AssignmentTargetKind> targetKinds() {
        return Set.of(AssignmentTargetKind.values());
    }

    @Override
    public AllAssignmentResult resolve(AssignmentContext context) {
        if (!"SANDBOX_REQUEST".equals(context.businessType())
                || !requests.linked(ConsoleWire.decimal(context.businessId(), false), context.instanceId())) {
            throw new IllegalStateException("Sandbox link not established");
        }
        return new AllAssignmentResult(List.of(
                new ResolvedTask("DIRECT", new TaskAssignmentTarget(AssignmentTargetKind.USER, settings.reviewer)),
                new ResolvedTask("FINANCE", SandboxSharedAssignmentProvider.FINANCE),
                new ResolvedTask("ROLE", SandboxSharedAssignmentProvider.APPROVER)), null);
    }
}
