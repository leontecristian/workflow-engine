package io.github.leontecristian.workflow.testhost.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;

import org.springframework.stereotype.Service;

@Service
public class ConsoleRuntimeAccess {
    private final WorkflowRuntimeQueryService reads;
    private final ConsoleContext context;

    public ConsoleRuntimeAccess(WorkflowRuntimeQueryService reads, ConsoleContext context) {
        this.reads = reads;
        this.context = context;
    }

    public WorkflowTaskView task(long id) {
        var task = reads.findTask(id).orElseThrow(ConsoleException::missing);
        if (!context.admin() && !context.actor().equals(task.assignedUserRef()))
            throw ConsoleException.missing();
        return task;
    }

    public WorkflowInstanceView instance(long id) {
        var instance = reads.findInstance(id).orElseThrow(ConsoleException::missing);
        if (!context.admin()) assigned(id, null);
        return instance;
    }

    public WorkflowActivityView activity(long id) {
        var activity = reads.findActivity(id).orElseThrow(ConsoleException::missing);
        if (!context.admin()) assigned(activity.instanceId(), id);
        return activity;
    }

    private void assigned(long instanceId, Long activityId) {
        long cursor = 0;
        // Includes completed tasks; reviewer access does not vanish on completion/retirement.
        for (int page = 0; page < 100; page++) {
            var tasks = reads.listTasks(instanceId, cursor, 100);
            if (tasks.items().stream()
                    .anyMatch(
                            t ->
                                    (activityId == null || t.activityId() == activityId)
                                            && context.actor().equals(t.assignedUserRef()))) return;
            if (tasks.nextAfterId() == null) throw ConsoleException.missing();
            cursor = tasks.nextAfterId();
        }
        throw new ConsoleException(422, "LIMIT_EXCEEDED");
    }
}
