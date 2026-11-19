package io.github.leontecristian.workflow.web.service;

import io.github.leontecristian.workflow.web.integration.WorkflowRequestIdentity;

/** Internal validation of the host's identity adapter result. */
public final class WorkflowRequestActors {
    private WorkflowRequestActors() {}

    public static String requireActor(WorkflowRequestIdentity identity) {
        String actorRef = identity.requireActorRef();
        if (actorRef == null || actorRef.isBlank()) {
            throw new IllegalStateException("Request identity returned no actor reference");
        }
        return actorRef;
    }
}
