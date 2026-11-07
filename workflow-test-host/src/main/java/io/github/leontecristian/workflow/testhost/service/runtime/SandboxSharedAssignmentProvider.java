package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentContext;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowSharedAssignmentProvider;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Development fixture only. Every lookup reads current immutable membership; no login-time cache. */
public class SandboxSharedAssignmentProvider implements WorkflowSharedAssignmentProvider {
    public static final TaskAssignmentTarget FINANCE = new TaskAssignmentTarget(AssignmentTargetKind.GROUP, "sandbox.finance");
    public static final TaskAssignmentTarget APPROVER = new TaskAssignmentTarget(AssignmentTargetKind.ROLE, "sandbox.approver");
    private final Map<TaskAssignmentTarget, Set<String>> memberships = new ConcurrentHashMap<>();

    public SandboxSharedAssignmentProvider(Set<String> reviewers) {
        replaceMembers(FINANCE, reviewers);
        replaceMembers(APPROVER, reviewers);
    }

    public void replaceMembers(TaskAssignmentTarget target, Set<String> actors) {
        if (target.kind() == AssignmentTargetKind.USER) throw new IllegalArgumentException("Shared target required");
        memberships.put(target, Set.copyOf(actors));
    }

    public void removeTarget(TaskAssignmentTarget target) { memberships.remove(target); }

    @Override public boolean targetExists(AssignmentContext context, TaskAssignmentTarget target) {
        return memberships.containsKey(target);
    }

    @Override public boolean isCurrentMember(TaskEligibilityContext context) {
        return memberships.getOrDefault(context.task().assignment(), Set.of()).contains(context.actorRef());
    }
}
