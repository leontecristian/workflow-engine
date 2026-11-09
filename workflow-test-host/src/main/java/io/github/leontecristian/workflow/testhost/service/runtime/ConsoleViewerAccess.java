package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.web.dto.instance.ViewerLinkReference;
import io.github.leontecristian.workflow.web.exception.instance.WorkflowViewerException;
import io.github.leontecristian.workflow.web.integration.instance.WorkflowViewerAccess;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Maps both viewer grants to the sandbox's retained-assignment policy. */
public class ConsoleViewerAccess implements WorkflowViewerAccess {
    private final ConsoleRuntimeAccess access;
    private final WorkflowRuntimeQueryService reads;
    public ConsoleViewerAccess(ConsoleRuntimeAccess access, WorkflowRuntimeQueryService reads) { this.access = access; this.reads = reads; }
    @Override public void requireInstanceRead(long id) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken)
            throw new WorkflowViewerException(401, "UNAUTHENTICATED");
        try { access.instance(id); }
        catch (ConsoleException e) { throw new WorkflowViewerException(e.status(), e.code()); }
    }
    @Override public void requireHistoryRead(long id) { requireInstanceRead(id); }
    @Override public void requireHistoryEventRead(WorkflowHistorySummary event) {
        WorkflowViewerAccess.super.requireHistoryEventRead(event);
        if ("TASK_COMPLETED".equals(event.eventType())) {
            if (event.taskId() == null) throw new WorkflowViewerException(500, "HOST_CONFIGURATION");
            try { access.task(event.taskId()); }
            catch (ConsoleException denied) { throw new WorkflowViewerException(denied.status(), denied.code()); }
        }
    }
    @Override public boolean canReadHistorySummary(WorkflowHistorySummary event) {
        if (!"TASK_CLAIMED".equals(event.eventType()) && !"TASK_RELEASED".equals(event.eventType()))
            return true;
        if (event.taskId() == null || event.activityId() == null)
            throw new WorkflowViewerException(500, "HOST_CONFIGURATION");
        try {
            var task = access.task(event.taskId());
            return task.instanceId() == event.instanceId() && task.activityId() == event.activityId();
        } catch (ConsoleException denied) {
            if (denied.status() == 404) return false;
            throw new WorkflowViewerException(denied.status(), denied.code());
        }
    }
    @Override public Set<ViewerLinkReference> readableLinks(long id, Set<ViewerLinkReference> candidates) {
        try { return links(id, candidates); }
        catch (ConsoleException e) { throw new WorkflowViewerException(e.status(), e.code()); }
    }
    private Set<ViewerLinkReference> links(long id, Set<ViewerLinkReference> candidates) {
        if (candidates.isEmpty()) return Set.of();
        var activities = access.readableActivities(id, candidates.stream().map(ViewerLinkReference::activityId).collect(java.util.stream.Collectors.toSet()));
        var grants = new HashSet<ViewerLinkReference>();
        // One bounded task scan, with each exact task checked independently of activity grants.
        var tasks = new HashSet<Long>(); long cursor = 0;
        for (int page = 0; page < 100; page++) {
            var result = reads.listTasks(id, cursor, 100);
            for (var task : result.items()) if (access.canReadTask(task)) tasks.add(task.id());
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
