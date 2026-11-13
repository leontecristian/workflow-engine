package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AllAssignmentResult;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentContext;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import java.util.Set;

/** Host-owned zero-input fixed ALL roster resolution, read-only with respect to workflow state. */
public interface AllTaskAssignmentResolver {
    String identifier();

    Set<AssignmentTargetKind> targetKinds();

    AllAssignmentResult resolve(AssignmentContext context);
}
