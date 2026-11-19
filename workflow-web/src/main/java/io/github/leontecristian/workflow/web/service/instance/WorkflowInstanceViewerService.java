package io.github.leontecristian.workflow.web.service.instance;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.web.dto.instance.ViewerLinkReference;
import io.github.leontecristian.workflow.web.exception.instance.WorkflowViewerException;
import io.github.leontecristian.workflow.web.integration.instance.WorkflowViewerAccess;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

/** Read projection only; all authorization is repeated for each request. */
@Transactional(readOnly = true)
public class WorkflowInstanceViewerService {
    private final WorkflowRuntimeQueryService reads;
    private final WorkflowViewerAccess access;
    private final ViewerWire wire;
    public WorkflowInstanceViewerService(WorkflowRuntimeQueryService reads, WorkflowViewerAccess access, ViewerWire wire) {
        this.reads = reads; this.access = access; this.wire = wire;
    }
    public Object instance(String actorRef, long id) {
        access.requireInstanceRead(actorRef, id);
        return reads.findInstance(id).orElseThrow(WorkflowViewerException::missing);
    }
    public Object history(String actorRef, long id, long after, int limit) {
        access.requireInstanceRead(actorRef, id);
        access.requireHistoryRead(actorRef, id);
        var page = reads.listHistory(id, after, limit);
        var visible = page.items().stream()
                .filter(event -> !isTaskRestrictedEvent(event)
                        || access.canReadHistorySummary(actorRef, event))
                .toList();
        return wire.page(visible, page.nextAfterSequence(), "nextAfterSequence", WorkflowHistorySummary::historySequence);
    }
    private static boolean isTaskRestrictedEvent(WorkflowHistorySummary event) {
        return "TASK_CLAIMED".equals(event.eventType()) || "TASK_RELEASED".equals(event.eventType())
                || "TASK_CANCELLED".equals(event.eventType()) || "TASK_REASSIGNED".equals(event.eventType());
    }

    /** Bound the final escaped envelope, independently of ordinary summary pages. */
    public String event(String actorRef, long id, long sequence) {
        access.requireInstanceRead(actorRef, id);
        access.requireHistoryRead(actorRef, id);
        var event = reads.findHistoryEvent(id, sequence, summary -> canReadExactTask(actorRef, summary))
                .orElseThrow(WorkflowViewerException::missing);
        if (isTaskRestrictedEvent(event.summary()) && !access.canReadHistorySummary(actorRef, event.summary()))
            throw WorkflowViewerException.missing();
        var summary = event.summary();
        if ("TASK_COMPLETED".equals(summary.eventType()) && summary.detailsSchemaVersion() >= 3
                && !canReadExactTask(actorRef, summary)) throw WorkflowViewerException.missing();
        access.requireHistoryEventRead(actorRef, summary);
        if (event.detailsJson().length()*2L > io.github.leontecristian.workflow.core.service.runtime.RuntimeJson.HISTORY_UTF16_BYTES
                || event.detailsJson().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > io.github.leontecristian.workflow.core.service.runtime.RuntimeJson.HISTORY_UTF8_BYTES) throw new WorkflowViewerException(422, "LIMIT_EXCEEDED");
        var json = wire.json(event);
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > io.github.leontecristian.workflow.core.service.runtime.RuntimeJson.HISTORY_RESPONSE_UTF8_BYTES)
            throw new WorkflowViewerException(422, "LIMIT_EXCEEDED");
        return json;
    }

    private boolean canReadExactTask(String actorRef, WorkflowHistorySummary summary) {
        if (summary.activityId() == null || summary.taskId() == null) return false;
        var exactTask = new ViewerLinkReference(summary.activityId(), summary.taskId());
        return access.readableLinks(actorRef, summary.instanceId(), Set.of(exactTask)).contains(exactTask);
    }
}
