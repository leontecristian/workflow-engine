package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.web.dto.runtime.WorkflowReassignmentTarget;
import io.github.leontecristian.workflow.web.dto.runtime.WorkflowReassignmentTargetPage;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowAssignmentDirectory;

/** Sandbox fixture directory for runtime task reads. */
public class ConsoleRuntimeReadHost implements WorkflowAssignmentDirectory {
    private final ConsoleWorkflowAuthorization authorization;
    private final ConsoleSettings settings;

    public ConsoleRuntimeReadHost(ConsoleWorkflowAuthorization authorization, ConsoleSettings settings) {
        this.authorization = authorization;
        this.settings = settings;
    }

    @Override public String assignmentLabel(WorkflowTaskView task) {
        var target = task.assignment();
        if (target.equals(SandboxSharedAssignmentProvider.FINANCE)) return "Finance team";
        if (target.equals(SandboxSharedAssignmentProvider.APPROVER)) return "Approvers";
        return target.kind() == AssignmentTargetKind.USER ? userLabel(target.ref()) : null;
    }

    String userLabel(String subjectRef) {
        if (settings.admin.equals(subjectRef)) return "Sandbox administrator";
        if (settings.reviewer.equals(subjectRef)) return "Sandbox reviewer";
        if (settings.reviewer2.equals(subjectRef)) return "Sandbox reviewer 2";
        return null;
    }

    @Override public WorkflowReassignmentTargetPage reassignmentTargets(TaskEligibilityContext task,
            AssignmentTargetKind kind, String query, String cursor, int limit) {
        boolean allowed;
        try { allowed = authorization.canAccessLinkedTask(task) && authorization.isAdministrator(task.actorRef()); }
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
