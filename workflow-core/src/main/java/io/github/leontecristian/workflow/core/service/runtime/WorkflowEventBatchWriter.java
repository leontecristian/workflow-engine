package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowEvent;
import java.util.List;

/**
 * Synchronous handoff of one command's ordered {@linkplain WorkflowEvent workflow events}
 * to host-owned durable storage.
 */
@FunctionalInterface
public interface WorkflowEventBatchWriter {
    /**
     * Persist every supplied event in list order in the existing command transaction before
     * returning. On success the writes participate in that transaction, whose owner decides
     * whether to commit or roll back. Propagate any write failure so partial writes and the
     * workflow changes roll back together.
     *
     * <p>Independent commits, reentry into workflow mutations, in-memory acknowledgment and
     * persistence deferred until after commit violate this contract.
     *
     * <p>After commit the host owns delivery, retries and idempotent consumption using the
     * stable event ID. Ordering applies within the supplied batch.
     *
     * @param events the command's events in history sequence order
     */
    void write(List<WorkflowEvent> events);
}
