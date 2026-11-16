package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.web.dto.runtime.WorkflowReassignmentTarget;
import io.github.leontecristian.workflow.web.dto.runtime.WorkflowReassignmentTargetPage;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowAssignmentDirectory;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowCancellationAvailabilityContextHost;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowRuntimeReadIdentity;

/** Sandbox identity, fixture directory and lazy cancellation context; core owns authorized reads. */
public class ConsoleRuntimeReadHost implements WorkflowRuntimeReadIdentity, WorkflowAssignmentDirectory,
        WorkflowCancellationAvailabilityContextHost {
    private final ConsoleContext context;
    private final ConsoleTaskReassignmentAuthorization reassignment;
    private final ConsoleSettings settings;

    public ConsoleRuntimeReadHost(ConsoleContext context, ConsoleTaskReassignmentAuthorization reassignment,
            ConsoleSettings settings) {
        this.context = context;
        this.reassignment = reassignment;
        this.settings = settings;
    }

    @Override public String currentActorRef() { return context.actor(); }
    @Override public WorkflowCommandContext currentCancellationContext() { return context.runtime(null); }

    @Override public WorkflowReassignmentTargetPage reassignmentTargets(TaskEligibilityContext task,
            AssignmentTargetKind kind, String query, String cursor, int limit) {
        boolean allowed;
        try { allowed = reassignment.canAccess(task) && reassignment.canReassign(task); }
        catch (RuntimeException failure) {
            throw new WorkflowExecutionException(WorkflowExecutionException.Category.AUTHORIZATION_LOOKUP_FAILED, failure);
        }
        if (!allowed) throw new WorkflowRuntimeException(403, "REASSIGNMENT_FORBIDDEN");
        if (kind == null || limit < 1 || limit > 50 || query != null && query.length() > 200)
            throw new WorkflowRuntimeException(400, "INVALID_INPUT");
        // Opaque continuation is an exact fixture reference, never a directory-wide browser cache.
        var candidates = new java.util.TreeMap<String, String>();
        if (kind == AssignmentTargetKind.USER) {
            for (String ref : java.util.List.of(settings.admin, settings.reviewer, settings.reviewer2)) candidates.put(ref, ref);
        } else if (kind == AssignmentTargetKind.GROUP) candidates.put(SandboxSharedAssignmentProvider.FINANCE.ref(), "Finance");
        else candidates.put(SandboxSharedAssignmentProvider.APPROVER.ref(), "Approver");
        if (cursor != null && !candidates.containsKey(cursor)) throw new WorkflowRuntimeException(400, "INVALID_INPUT");
        String search = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
        var selected = candidates.entrySet().stream()
                .filter(entry -> cursor == null || entry.getKey().compareTo(cursor) > 0)
                .filter(entry -> entry.getKey().toLowerCase(java.util.Locale.ROOT).contains(search)
                        || entry.getValue().toLowerCase(java.util.Locale.ROOT).contains(search))
                .limit(limit + 1L).toList();
        boolean more = selected.size() > limit;
        var items = selected.stream().limit(limit)
                .map(entry -> new WorkflowReassignmentTarget(entry.getKey(), entry.getValue())).toList();
        return new WorkflowReassignmentTargetPage(items, more ? items.get(items.size() - 1).ref() : null);
    }
}
