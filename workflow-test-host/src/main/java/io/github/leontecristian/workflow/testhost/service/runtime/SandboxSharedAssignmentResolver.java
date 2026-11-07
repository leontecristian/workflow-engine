package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentContext;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.ResolvedTask;
import io.github.leontecristian.workflow.core.dto.runtime.SingleAssignmentResult;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.service.runtime.SingleTaskAssignmentResolver;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import java.util.List;
import java.util.Set;

/** New stable IDs keep the existing direct-reviewer resolver's contract unchanged. */
public class SandboxSharedAssignmentResolver implements SingleTaskAssignmentResolver {
    private final String identifier;
    private final TaskAssignmentTarget target;
    private final SandboxRequestStore requests;
    public SandboxSharedAssignmentResolver(String identifier, TaskAssignmentTarget target, SandboxRequestStore requests) {
        this.identifier = identifier; this.target = target; this.requests = requests;
    }
    @Override public String identifier() { return identifier; }
    @Override public Set<AssignmentTargetKind> targetKinds() { return Set.of(target.kind()); }
    @Override public SingleAssignmentResult resolve(AssignmentContext context) {
        if (!"SANDBOX_REQUEST".equals(context.businessType())
                || !requests.linked(ConsoleWire.decimal(context.businessId(), false), context.instanceId()))
            throw new IllegalStateException("Sandbox link not established");
        return new SingleAssignmentResult(List.of(new ResolvedTask("REVIEW", target)), null);
    }
}
