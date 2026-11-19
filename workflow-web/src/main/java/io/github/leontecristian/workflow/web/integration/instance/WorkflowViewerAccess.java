package io.github.leontecristian.workflow.web.integration.instance;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.web.dto.instance.ViewerLinkReference;
import java.util.Set;

/** Host-owned authorization for the actor captured through the trusted request identity adapter. */
public interface WorkflowViewerAccess {
    void requireInstanceRead(String actorRef, long instanceId);
    /** Full retained evidence, including actors, comments and business snapshots. */
    void requireHistoryRead(String actorRef, long instanceId);
    /** Additional host authorization for exact evidence. Task input and structured result details also require an exact-task link grant. */
    default void requireHistoryEventRead(String actorRef, WorkflowHistorySummary event) {
        requireHistoryRead(actorRef, event.instanceId());
    }

    /** Ownership summaries and details require an exact task grant. */
    default boolean canReadHistorySummary(String actorRef, WorkflowHistorySummary event) {
        if (!"TASK_CLAIMED".equals(event.eventType()) && !"TASK_RELEASED".equals(event.eventType())
                && !"TASK_CANCELLED".equals(event.eventType()) && !"TASK_REASSIGNED".equals(event.eventType()))
            return true;
        if (event.activityId() == null || event.taskId() == null) return false;
        var task = new ViewerLinkReference(event.activityId(), event.taskId());
        return readableLinks(actorRef, event.instanceId(), Set.of(task)).contains(task);
    }
    /** Return only candidate activity/exact-task grants; never infer one from the other. */
    Set<ViewerLinkReference> readableLinks(String actorRef, long instanceId, Set<ViewerLinkReference> candidates);
}
