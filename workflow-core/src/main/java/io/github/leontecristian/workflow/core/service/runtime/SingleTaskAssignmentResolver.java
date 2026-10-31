package io.github.leontecristian.workflow.core.service.runtime;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentContext;
import io.github.leontecristian.workflow.core.dto.runtime.SingleAssignmentResult;
/** A real zero-input SINGLE resolver, read-only with respect to workflow state. IDs are stable and exact. */
public interface SingleTaskAssignmentResolver {
    String identifier();
    SingleAssignmentResult resolve(AssignmentContext context);
}
