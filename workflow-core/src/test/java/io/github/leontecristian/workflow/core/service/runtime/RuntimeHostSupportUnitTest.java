package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.AllAssignmentResult;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentContext;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.CompletionPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.ResolvedTask;
import io.github.leontecristian.workflow.core.dto.runtime.SingleAssignmentResult;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowEvent;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RuntimeHostSupportUnitTest {
    @Test
    void handsOffTheEntireBatchOnceInOrder() {
        List<List<WorkflowEvent>> writes = new ArrayList<>();
        var host = hostWith(writes::add);
        var commandId = UUID.randomUUID();
        var events = List.of(event(commandId, 1), event(commandId, 2), event(commandId, 3));

        host.writeEvents(host.eventBatchWriter(), events);

        assertThat(writes).containsExactly(events);
    }

    @Test
    void propagatesWriterFailureThroughExistingTranslation() {
        var cause = new IllegalStateException("Durable storage failed");
        var host = hostWith(events -> { throw cause; });
        var events = List.of(event(UUID.randomUUID(), 1));

        var failure = assertThrows(WorkflowExecutionException.class,
                () -> host.writeEvents(host.eventBatchWriter(), events));

        assertThat(failure.category()).isEqualTo(Category.HOST_CONFIGURATION);
        assertThat(failure).hasCause(cause);
    }

    @Test
    void resolvesSingleAndAllBySeparateRegistrationsOfTheSameIdentifier() {
        var beans = new DefaultListableBeanFactory();
        var context = mock(AssignmentContext.class);
        var alice = new ResolvedTask("first", new TaskAssignmentTarget(AssignmentTargetKind.USER, "alice"));
        var bob = new ResolvedTask("second", new TaskAssignmentTarget(AssignmentTargetKind.USER, "bob"));
        var single = single("reviewer", Set.of(AssignmentTargetKind.USER));
        var all = mock(AllTaskAssignmentResolver.class);
        when(all.identifier()).thenReturn("reviewer");
        when(all.targetKinds()).thenReturn(Set.of(AssignmentTargetKind.USER));
        when(single.resolve(context)).thenReturn(new SingleAssignmentResult(List.of(alice), "single"));
        when(all.resolve(context)).thenReturn(new AllAssignmentResult(List.of(alice, bob), "all"));
        beans.registerSingleton("singleBean", single);
        beans.registerSingleton("allBean", all);
        var host = hostWith(beans);
        assertThat(host.resolve("reviewer", context).tasks()).containsExactly(alice);
        assertThat(host.resolveAll("reviewer", context).tasks()).containsExactly(alice, bob);
        assertThat(assertThrows(WorkflowExecutionException.class, () -> host.resolve("absent", context)).category())
                .isEqualTo(Category.HOST_CONFIGURATION);
    }

    @Test
    void duplicateResolverIdentifiersFailOnlyWhenTheirPolicyRegistryIsConsumed() {
        var beans = new DefaultListableBeanFactory();
        var first = single("same", Set.of(AssignmentTargetKind.USER));
        var duplicate = single("same", Set.of(AssignmentTargetKind.USER));
        beans.registerSingleton("first", first);
        beans.registerSingleton("duplicate", duplicate);
        var host = hostWith(beans);
        assertThat(host.assignmentKinds("same", CompletionPolicy.ALL)).isNull();
        verifyNoInteractions(first, duplicate);
        assertThat(assertThrows(WorkflowExecutionException.class,
                () -> host.assignmentKinds("same", CompletionPolicy.SINGLE)).category()).isEqualTo(Category.HOST_CONFIGURATION);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void malformedResolverKindsAndIdentifiersFailWhenConsumed() {
        for (var invalid : List.of(single(" ", Set.of(AssignmentTargetKind.USER)),
                single("reviewer", Set.of()), single("reviewer", (Set) Set.of("USER")),
                single("reviewer", (Set) EnumSet.of(CompletionPolicy.SINGLE)))) {
            var beans = new DefaultListableBeanFactory();
            beans.registerSingleton("invalid", invalid);
            var host = hostWith(beans);
            verifyNoInteractions(invalid);
            assertThat(assertThrows(WorkflowExecutionException.class,
                    () -> host.requireAssignmentResolver("reviewer", CompletionPolicy.SINGLE)).category())
                    .isEqualTo(Category.HOST_CONFIGURATION);
        }
    }

    private static SingleTaskAssignmentResolver single(String identifier, Set<AssignmentTargetKind> kinds) {
        var bean = mock(SingleTaskAssignmentResolver.class);
        when(bean.identifier()).thenReturn(identifier);
        when(bean.targetKinds()).thenReturn(kinds);
        return bean;
    }

    private static RuntimeHostSupport hostWith(WorkflowEventBatchWriter writer) {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("eventBatchWriter", writer);
        return hostWith(beans);
    }

    private static RuntimeHostSupport hostWith(DefaultListableBeanFactory beans) {
        return new RuntimeHostSupport(beans.getBeanProvider(SingleTaskAssignmentResolver.class),
                beans.getBeanProvider(WorkflowEventBatchWriter.class),
                mock(WorkflowTaskEligibilityService.class),
                beans.getBeanProvider(AllTaskAssignmentResolver.class));
    }

    private static WorkflowEvent event(UUID commandId, long sequence) {
        return new WorkflowEvent((short) 1, "120:" + sequence, commandId, 120, 42, sequence,
                "TRANSITION_TAKEN", ActorKind.USER, "alex", Instant.EPOCH, null,
                null, null, "from", "to", "edge", Map.of());
    }
}
