package io.github.leontecristian.workflow.web.integration.instance;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.web.dto.instance.ViewerLinkReference;
import java.util.Set;

/** Host-owned trusted authentication and policy. All failures must deny access explicitly. */
public interface WorkflowViewerAccess {
    void requireInstanceRead(long instanceId);
    /** Full retained evidence, including actors, comments and business snapshots. */
    void requireHistoryRead(long instanceId);
    /** Additional host authorization for exact evidence. Task input and structured result details also require an exact-task link grant. */
    default void requireHistoryEventRead(WorkflowHistorySummary event) {
        requireHistoryRead(event.instanceId());
    }

    /** Ownership summaries and details require an exact task grant. */
    default boolean canReadHistorySummary(WorkflowHistorySummary event) {
        if (!"TASK_CLAIMED".equals(event.eventType()) && !"TASK_RELEASED".equals(event.eventType()))
            return true;
        if (event.activityId() == null || event.taskId() == null) return false;
        var task = new ViewerLinkReference(event.activityId(), event.taskId());
        return readableLinks(event.instanceId(), Set.of(task)).contains(task);
    }
    /** Return only candidate activity/exact-task grants; never infer one from the other. */
    Set<ViewerLinkReference> readableLinks(long instanceId, Set<ViewerLinkReference> candidates);
}
