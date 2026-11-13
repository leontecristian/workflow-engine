package io.github.leontecristian.workflow.web.service.instance;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.web.dto.instance.ViewerLinkReference;
import io.github.leontecristian.workflow.web.integration.instance.WorkflowViewerAccess;
import io.github.leontecristian.workflow.web.exception.instance.WorkflowViewerException;
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
    public Object instance(long id) {
        access.requireInstanceRead(id);
        return reads.findInstance(id).orElseThrow(WorkflowViewerException::missing);
    }
    public Object history(long id, long after, int limit) {
        access.requireInstanceRead(id); access.requireHistoryRead(id);
        var page = reads.listHistory(id, after, limit);
        var visible = page.items().stream()
                .filter(event -> !isOwnershipEvent(event)
                        || access.canReadHistorySummary(event))
                .toList();
        return wire.page(visible, page.nextAfterSequence(), "nextAfterSequence", WorkflowHistorySummary::historySequence);
    }
    private static boolean isOwnershipEvent(WorkflowHistorySummary event) {
        return "TASK_CLAIMED".equals(event.eventType()) || "TASK_RELEASED".equals(event.eventType());
    }

    /** Bound the final escaped envelope, independently of ordinary summary pages. */
    public String event(long id, long sequence) {
        access.requireInstanceRead(id); access.requireHistoryRead(id);
        var event = reads.findHistoryEvent(id, sequence, this::canReadExactTask)
                .orElseThrow(WorkflowViewerException::missing);
        if (isOwnershipEvent(event.summary()) && !access.canReadHistorySummary(event.summary()))
            throw WorkflowViewerException.missing();
        var summary = event.summary();
        if ("TASK_COMPLETED".equals(summary.eventType()) && summary.detailsSchemaVersion() >= 3
                && !canReadExactTask(summary)) throw WorkflowViewerException.missing();
        access.requireHistoryEventRead(event.summary());
        if (event.detailsJson().length()*2L > io.github.leontecristian.workflow.core.service.runtime.RuntimeJson.HISTORY_UTF16_BYTES
                || event.detailsJson().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > io.github.leontecristian.workflow.core.service.runtime.RuntimeJson.HISTORY_UTF8_BYTES) throw new WorkflowViewerException(422, "LIMIT_EXCEEDED");
        var json = wire.json(event);
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > io.github.leontecristian.workflow.core.service.runtime.RuntimeJson.HISTORY_RESPONSE_UTF8_BYTES)
            throw new WorkflowViewerException(422, "LIMIT_EXCEEDED");
        return json;
    }

    private boolean canReadExactTask(WorkflowHistorySummary summary) {
        if (summary.activityId() == null || summary.taskId() == null) return false;
        var exactTask = new ViewerLinkReference(summary.activityId(), summary.taskId());
        return access.readableLinks(summary.instanceId(), Set.of(exactTask)).contains(exactTask);
    }
}
