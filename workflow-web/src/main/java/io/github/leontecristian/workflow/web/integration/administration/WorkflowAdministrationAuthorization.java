package io.github.leontecristian.workflow.web.integration.administration;

/** Host policy for the same trusted actor obtained by shared web. */
public interface WorkflowAdministrationAuthorization {
    void requireAdministrationAccess(String actorRef);
}
