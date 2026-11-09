package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstancePage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceQuery;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowRuntimeReadHost;
import java.util.function.Supplier;
/** Sandbox policy; source-page filtering and continuation intentionally remain host-owned. */
public class ConsoleRuntimeReadHost implements WorkflowRuntimeReadHost {
    private final WorkflowRuntimeQueryService reads;
    private final ConsoleRuntimeAccess access;
    private final ConsoleContext context;
    public ConsoleRuntimeReadHost(WorkflowRuntimeQueryService reads, ConsoleRuntimeAccess access, ConsoleContext context) {
        this.reads = reads; this.access = access; this.context = context;
    }
    @Override public void requireMonitorAccess() {
        if (!context.admin()) throw new WorkflowRuntimeException(403, "FORBIDDEN");
    }
    @Override public WorkflowInstancePage listInstances(WorkflowInstanceQuery query) {
        requireMonitorAccess();
        return reads.listInstances(query);
    }
    @Override public WorkflowTaskPage listAssignedTasks(long afterId, int limit) {
        return reads.listEligibleTasks(context.actor(), afterId, limit, access::canAccessBusiness);
    }
    @Override public WorkflowTaskPage listTasks(long id, long afterId, int limit) {
        instance(id);
        var visible = new java.util.ArrayList<WorkflowTaskView>();
        long cursor = afterId;
        int examined = 0;
        while (examined < 500) {
            var page = reads.listTasks(id, cursor, Math.min(limit, 500 - examined));
            for (int index = 0; index < page.items().size(); index++) {
                var task = page.items().get(index);
                cursor = task.id(); examined++;
                if (access.canReadTask(task)) visible.add(task);
                if (visible.size() == limit || examined == 500) {
                    boolean more = index + 1 < page.items().size() || page.nextAfterId() != null;
                    return new WorkflowTaskPage(visible, more ? cursor : null);
                }
            }
            if (page.nextAfterId() == null) return new WorkflowTaskPage(visible, null);
            cursor = page.nextAfterId();
        }
        return new WorkflowTaskPage(visible, cursor);
    }
    @Override public boolean canCompleteTask(WorkflowTaskView task) { return access.canCompleteTask(task); }
    @Override public boolean canReadTask(WorkflowTaskView task) { return access.canReadTask(task); }
    @Override public boolean canClaimTask(WorkflowTaskView task) { return access.canClaimTask(task); }
    @Override public boolean canReleaseTask(WorkflowTaskView task) { return access.canReleaseTask(task); }
    @Override public boolean canAdministrativelyReleaseTask(WorkflowTaskView task) {
        return access.canAdministrativelyReleaseTask(task);
    }
    @Override public WorkflowInstanceView instance(long id) { return policy(() -> access.instance(id)); }
    @Override public WorkflowTaskView task(long id) { return policy(() -> access.task(id)); }
    @Override public WorkflowActivityView activity(long id) { return policy(() -> access.activity(id)); }
    private <T> T policy(Supplier<T> operation) {
        try { return operation.get(); }
        catch (ConsoleException e) { throw new WorkflowRuntimeException(e.status(), e.code()); }
    }
}
