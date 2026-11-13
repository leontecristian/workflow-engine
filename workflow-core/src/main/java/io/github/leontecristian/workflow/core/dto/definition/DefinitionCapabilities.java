package io.github.leontecristian.workflow.core.dto.definition;
import java.util.Set;
/** Immutable deployment descriptors; duplicate logical IDs are rejected by core on consumption. */
public record DefinitionCapabilities(Set<SingleAssignmentCapability> assignments, Set<TaskTypeCapability> taskTypes,
        Set<AllAssignmentCapability> allAssignments) {
    public DefinitionCapabilities {
        assignments = Set.copyOf(assignments); taskTypes = Set.copyOf(taskTypes);
        allAssignments = Set.copyOf(allAssignments);
    }
    public DefinitionCapabilities(Set<SingleAssignmentCapability> assignments, Set<TaskTypeCapability> taskTypes) {
        this(assignments, taskTypes, Set.of());
    }
}
