package io.github.leontecristian.workflow.core.dto.definition;

/** Immutable in-process definition contract; the host owns authorization and transport mapping. */
public record SingleAssignmentCapability(String resolver,
        java.util.Set<io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind> targetKinds) {
    public SingleAssignmentCapability {
        targetKinds = java.util.Set.copyOf(targetKinds);
        if (targetKinds.isEmpty()) throw new IllegalArgumentException("Target kinds must not be empty");
    }
}
