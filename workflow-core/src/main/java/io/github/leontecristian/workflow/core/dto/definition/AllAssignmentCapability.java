package io.github.leontecristian.workflow.core.dto.definition;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import java.util.Set;

/** Host descriptor for a deployed zero-input ALL roster resolver. */
public record AllAssignmentCapability(String resolver, Set<AssignmentTargetKind> targetKinds) {
    public AllAssignmentCapability {
        targetKinds = Set.copyOf(targetKinds);
        if (targetKinds.isEmpty()) throw new IllegalArgumentException("Target kinds must not be empty");
    }
}
