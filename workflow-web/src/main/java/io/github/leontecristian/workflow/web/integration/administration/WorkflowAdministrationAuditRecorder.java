package io.github.leontecristian.workflow.web.integration.administration;

import io.github.leontecristian.workflow.web.dto.administration.WorkflowAdministrationAudit;

/** Persist supplied evidence synchronously in the mutation transaction; throw unchecked on failure. */
public interface WorkflowAdministrationAuditRecorder {
    void record(WorkflowAdministrationAudit audit);
}
