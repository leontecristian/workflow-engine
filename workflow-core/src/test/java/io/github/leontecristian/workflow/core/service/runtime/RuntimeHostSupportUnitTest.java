package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowEvent;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.service.definition.DefinitionCapabilityProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

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

    private static RuntimeHostSupport hostWith(WorkflowEventBatchWriter writer) {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("eventBatchWriter", writer);
        return new RuntimeHostSupport(beans.getBeanProvider(SingleTaskAssignmentResolver.class),
                beans.getBeanProvider(WorkflowEventBatchWriter.class),
                mock(WorkflowTaskEligibilityService.class),
                beans.getBeanProvider(DefinitionCapabilityProvider.class));
    }

    private static WorkflowEvent event(UUID commandId, long sequence) {
        return new WorkflowEvent((short) 1, "120:" + sequence, commandId, 120, 42, sequence,
                "TRANSITION_TAKEN", ActorKind.USER, "alex", Instant.EPOCH, null,
                null, null, "from", "to", "edge", Map.of());
    }
}
