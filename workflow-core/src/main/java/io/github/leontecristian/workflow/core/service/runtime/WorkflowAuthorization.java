package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowAuthorizationRequest;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowOperation;
import java.util.Set;

/** Host business policy for workflow commands. A host can register a single bean. */
public interface WorkflowAuthorization {
    /** Declares enabled operations once at wiring. Business starts remain host-owned. */
    Set<WorkflowOperation> supportedOperations();

    /** Evaluates current policy. Return false for denial and throw when the lookup fails. */
    boolean isAllowed(WorkflowAuthorizationRequest request);
}
