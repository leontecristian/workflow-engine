package io.github.leontecristian.workflow.web.integration.instance;

import io.github.leontecristian.workflow.web.dto.instance.ViewerLinkReference;
import java.util.Set;

/** Host-owned trusted authentication and policy. All failures must deny access explicitly. */
public interface WorkflowViewerAccess {
    void requireInstanceRead(long instanceId);
    /** Full retained evidence, including actors, comments and business snapshots. */
    void requireHistoryRead(long instanceId);
    /** Return only candidate activity/exact-task grants; never infer one from the other. */
    Set<ViewerLinkReference> readableLinks(long instanceId, Set<ViewerLinkReference> candidates);
}
