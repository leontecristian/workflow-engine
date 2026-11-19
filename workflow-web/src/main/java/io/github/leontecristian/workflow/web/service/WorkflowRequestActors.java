package io.github.leontecristian.workflow.web.service;

import io.github.leontecristian.workflow.web.integration.WorkflowRequestIdentity;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** Internal validation of the host's identity adapter result. */
public final class WorkflowRequestActors {
    private static final String ACTOR_ATTRIBUTE = WorkflowRequestActors.class.getName() + ".actor";

    private WorkflowRequestActors() {}

    public static String requireActor(WorkflowRequestIdentity identity) {
        var request = RequestContextHolder.getRequestAttributes();
        if (request != null
                && request.getAttribute(ACTOR_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof RequestActor actor
                && actor.identity() == identity) {
            return actor.ref();
        }
        String actorRef = identity.requireActorRef();
        if (actorRef == null || actorRef.isBlank()) {
            throw new IllegalStateException("Request identity returned no actor reference");
        }
        if (request != null)
            request.setAttribute(ACTOR_ATTRIBUTE, new RequestActor(identity, actorRef), RequestAttributes.SCOPE_REQUEST);
        return actorRef;
    }

    private record RequestActor(WorkflowRequestIdentity identity, String ref) {}
}
