package io.github.leontecristian.workflow.web.integration;

import io.github.leontecristian.workflow.web.exception.WorkflowUnauthenticatedException;

/** Resolves the stable actor reference from trusted authentication for the current request. */
@FunctionalInterface
public interface WorkflowRequestIdentity {
    /**
     * @return the authenticated actor's nonblank reference
     * @throws WorkflowUnauthenticatedException if no authenticated actor is present
     */
    String requireActorRef();
}
