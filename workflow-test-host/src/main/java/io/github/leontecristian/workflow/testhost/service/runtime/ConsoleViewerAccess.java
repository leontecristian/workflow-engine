package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.web.dto.instance.ViewerLinkReference;
import io.github.leontecristian.workflow.web.exception.instance.WorkflowViewerException;
import io.github.leontecristian.workflow.web.integration.instance.WorkflowViewerAccess;
import java.util.HashSet;
import java.util.Set;

/** Applies sandbox instance access and the administrator grant for retained evidence. */
public class ConsoleViewerAccess implements WorkflowViewerAccess {
    private final ConsoleRuntimeAccess access;
    private final WorkflowRuntimeQueryService reads;
    private final ConsoleContext identity;
    public ConsoleViewerAccess(ConsoleRuntimeAccess access, WorkflowRuntimeQueryService reads, ConsoleContext identity) {
        this.access = access;
        this.reads = reads;
        this.identity = identity;
    }
    @Override public void requireInstanceRead(String actorRef, long id) {
        try { access.instance(actorRef, id); }
        catch (ConsoleException e) { throw new WorkflowViewerException(e.status(), e.code()); }
    }
    @Override public void requireHistoryRead(String actorRef, long id) {
        if (!identity.isConsoleAdministrator(actorRef)) throw WorkflowViewerException.missing();
        requireInstanceRead(actorRef, id);
    }
    @Override public void requireHistoryEventRead(String actorRef, WorkflowHistorySummary event) {
        WorkflowViewerAccess.super.requireHistoryEventRead(actorRef, event);
        if ("TASK_COMPLETED".equals(event.eventType())) {
            if (event.taskId() == null) throw new WorkflowViewerException(500, "HOST_CONFIGURATION");
            try { access.task(actorRef, event.taskId()); }
            catch (ConsoleException denied) { throw new WorkflowViewerException(denied.status(), denied.code()); }
        }
    }
    @Override public boolean canReadHistorySummary(String actorRef, WorkflowHistorySummary event) {
        if (!"TASK_CLAIMED".equals(event.eventType()) && !"TASK_RELEASED".equals(event.eventType())
                && !"TASK_CANCELLED".equals(event.eventType()) && !"TASK_REASSIGNED".equals(event.eventType()))
            return true;
        if (event.taskId() == null || event.activityId() == null)
            throw new WorkflowViewerException(500, "HOST_CONFIGURATION");
        return canReadExactTask(actorRef, event);
    }

    boolean canReadExactTask(String actorRef, WorkflowHistorySummary event) {
        if (event.taskId() == null || event.activityId() == null) return false;
        try {
            var task = access.task(actorRef, event.taskId());
            return task.instanceId() == event.instanceId() && task.activityId() == event.activityId();
        } catch (ConsoleException denied) {
            if (denied.status() == 404) return false;
            throw new WorkflowViewerException(denied.status(), denied.code());
        }
    }
    @Override public Set<ViewerLinkReference> readableLinks(String actorRef, long id, Set<ViewerLinkReference> candidates) {
        try { return links(actorRef, id, candidates); }
        catch (ConsoleException e) { throw new WorkflowViewerException(e.status(), e.code()); }
    }
    private Set<ViewerLinkReference> links(String actorRef, long id, Set<ViewerLinkReference> candidates) {
        if (candidates.isEmpty()) return Set.of();
        var activities = access.readableActivities(actorRef, id,
                candidates.stream().map(ViewerLinkReference::activityId).collect(java.util.stream.Collectors.toSet()));
        var grants = new HashSet<ViewerLinkReference>();
        // One bounded task scan, with each exact task checked independently of activity grants.
        var tasks = new HashSet<Long>(); long cursor = 0;
        for (int page = 0; page < 100; page++) {
            var result = reads.listTasks(id, cursor, 100);
            for (var task : result.items())
                if (access.canReadTask(new TaskEligibilityContext(task, actorRef))) tasks.add(task.id());
            if (result.nextAfterId() == null) {
                for (var candidate : candidates) {
                    if (activities.contains(candidate.activityId())) grants.add(new ViewerLinkReference(candidate.activityId(), null));
                    if (candidate.taskId() != null && tasks.contains(candidate.taskId())) grants.add(candidate);
                }
                return Set.copyOf(grants);
            }
            cursor = result.nextAfterId();
        }
        throw new WorkflowViewerException(422, "LIMIT_EXCEEDED");
    }
}
