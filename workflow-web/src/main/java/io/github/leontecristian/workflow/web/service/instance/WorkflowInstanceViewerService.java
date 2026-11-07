package io.github.leontecristian.workflow.web.service.instance;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.web.integration.instance.WorkflowViewerAccess;
import io.github.leontecristian.workflow.web.exception.instance.WorkflowViewerException;
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
        return wire.page(page.items(), page.nextAfterSequence(), "nextAfterSequence", WorkflowHistorySummary::historySequence);
    }
    /** Bound the final escaped envelope, independently of ordinary summary pages. */
    public String event(long id, long sequence) {
        access.requireInstanceRead(id); access.requireHistoryRead(id);
        var event = reads.findHistoryEvent(id, sequence).orElseThrow(WorkflowViewerException::missing);
        access.requireHistoryEventRead(event.summary());
        if (!wire.fits(event.detailsJson(), 4 * 1024 * 1024)) throw new WorkflowViewerException(422, "LIMIT_EXCEEDED");
        var json = wire.json(event);
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 8 * 1024 * 1024)
            throw new WorkflowViewerException(422, "LIMIT_EXCEEDED");
        return json;
    }
}
