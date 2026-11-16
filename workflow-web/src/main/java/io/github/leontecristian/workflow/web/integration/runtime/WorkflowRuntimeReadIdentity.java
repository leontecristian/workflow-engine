package io.github.leontecristian.workflow.web.integration.runtime;

/** Actor reference obtained exclusively from the host's authenticated server context. */
public interface WorkflowRuntimeReadIdentity {
    String currentActorRef();
}
