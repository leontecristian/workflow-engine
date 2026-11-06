package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Assignment;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Human;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Node;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Outcome;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Transition;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class HumanOutcomeRoutingUnitTest {
    private final RuntimeJson json = new RuntimeJson();
    private MinimalWorkflowDefinition model(List<Transition> edges) {
        var approved = new Node("APPROVED", "END", "Approved", "APPROVED");
        return new MinimalWorkflowDefinition(new Node("BEGIN", "START", "Start", null),
                new Human("REVIEW", "REVIEW", new Assignment("resolver"), List.of(
                        new Outcome("REJECT", "Reject", true), new Outcome("APPROVE", "Approve", false))),
                approved, edges, Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("APPROVED", approved, "REJECTED", new Node("REJECTED", "END", "Rejected", "REJECTED")), java.util.Set.of());
    }
    @Test void explicitKeySelectsRouteAndEvidenceRegardlessOfArrayOrder() {
        var edges = new ArrayList<>(List.of(new Transition("approve", "REVIEW", "APPROVED", "APPROVE"),
                new Transition("reject", "REVIEW", "REJECTED", "REJECT")));
        for (int order = 0; order < 2; order++) {
            Collections.reverse(edges);
            for (String outcome : List.of("APPROVE", "REJECT")) {
                var em = mock(EntityManager.class);
                var actions = mock(ServiceActionRegistry.class);
                var variables = mock(RuntimeVariables.class);
                var context = new WorkflowCommandContext(ActorKind.USER, "reviewer", Instant.EPOCH, "test");
                var batch = new RuntimeHistoryBatch(em, json, 1, 1, UUID.randomUUID(), context);
                var end = new RuntimeServiceProgression(actions, variables, json).traverse(model(edges), "REVIEW", null,
                        new ServiceActionContext(UUID.randomUUID(), 1, 1, "REVIEW", null, null, context),
                        Map.of(), new RuntimeProgression(), batch, "hash", outcome);
                assertThat(end.result()).isEqualTo(outcome.equals("APPROVE") ? "APPROVED" : "REJECTED");
                assertThat(batch.events()).singleElement().satisfies(event -> {
                    assertThat(event.transitionKey()).isEqualTo(outcome.toLowerCase(java.util.Locale.ROOT));
                    assertThat(event.payload()).containsExactlyEntriesOf(Map.of("outcomeKey", outcome));
                });
                var fact = ArgumentCaptor.forClass(WorkflowHistoryEntity.class);
                verify(em).persist(fact.capture());
                assertThat(json.tree(fact.getValue().getDetailsJson()).path("outcomeKey").asText()).isEqualTo(outcome);
                verifyNoInteractions(actions, variables);
            }
        }
    }
    @Test void missingAndAmbiguousPinnedRoutesFailBeforeEvidenceOrEffects() {
        for (var edges : List.of(List.<Transition>of(), List.of(new Transition("one", "REVIEW", "APPROVED", "APPROVE"),
                new Transition("two", "REVIEW", "REJECTED", "APPROVE")))) {
            var em = mock(EntityManager.class);
            var actions = mock(ServiceActionRegistry.class);
            var context = new WorkflowCommandContext(ActorKind.USER, "reviewer", Instant.EPOCH, "test");
            var batch = new RuntimeHistoryBatch(em, json, 1, 1, UUID.randomUUID(), context);
            assertThatThrownBy(() -> new RuntimeServiceProgression(actions, mock(RuntimeVariables.class), json).traverse(model(edges), "REVIEW", null,
                    new ServiceActionContext(UUID.randomUUID(), 1, 1, "REVIEW", null, null, context), Map.of(), new RuntimeProgression(), batch, "hash", "APPROVE"))
                    .isInstanceOfSatisfying(WorkflowExecutionException.class, error -> assertThat(error.category()).isEqualTo(WorkflowExecutionException.Category.HOST_CONFIGURATION));
            verifyNoInteractions(em, actions);
        }
    }
}
