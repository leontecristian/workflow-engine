package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.definition.AllAssignmentCapability;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionCapabilities;
import io.github.leontecristian.workflow.core.dto.runtime.AllAssignmentResult;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.ResolvedTask;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllAssignmentContractUnitTest {
    private final ResolvedTask task = new ResolvedTask("review", new TaskAssignmentTarget(AssignmentTargetKind.USER, "reviewer"));

    @Test
    void checksCardinalityBeforeReadingProviderElements() {
        for (int count : new int[]{0, 101, Integer.MAX_VALUE}) {
            List<ResolvedTask> oversized = new AbstractList<>() {
                @Override public int size() { return count; }
                @Override public ResolvedTask get(int index) { throw new AssertionError("Must not traverse invalid roster"); }
            };
            assertThatThrownBy(() -> new AllAssignmentResult(oversized, null))
                    .isInstanceOfSatisfying(WorkflowExecutionException.class, failure ->
                            assertThat(failure.category()).isEqualTo(WorkflowExecutionException.Category.INVALID_ASSIGNMENT));
        }
        assertThatThrownBy(() -> new AllAssignmentResult(null, null)).isInstanceOf(WorkflowExecutionException.class);
        var nullTask = new ArrayList<ResolvedTask>(); nullTask.add(null);
        assertThatThrownBy(() -> new AllAssignmentResult(nullTask, null)).isInstanceOf(WorkflowExecutionException.class);
    }

    @Test
    void retainsAnImmutableBoundedSnapshotAndSingleCapabilityConstructor() {
        var providerTasks = new ArrayList<>(java.util.Collections.nCopies(100, task));
        var result = new AllAssignmentResult(providerTasks, "revision");
        providerTasks.clear();
        assertThat(result.tasks()).hasSize(100);
        assertThat(result.reviewRevisionRef()).isEqualTo("revision");
        assertThatThrownBy(() -> result.tasks().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new AllAssignmentResult(List.of(task), null).tasks()).containsExactly(task);
        assertThat(new DefinitionCapabilities(Set.of(), Set.of()).allAssignments()).isEmpty();
        assertThatThrownBy(() -> new AllAssignmentCapability("reviewers", Set.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
