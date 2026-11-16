package io.github.leontecristian.workflow.web.integration.administration;

/** Trusted authenticated actor. Unauthenticated calls must fail with 401; actor must be nonblank. */
public interface WorkflowAdministrationIdentity {
    String requireActorRef();
}
