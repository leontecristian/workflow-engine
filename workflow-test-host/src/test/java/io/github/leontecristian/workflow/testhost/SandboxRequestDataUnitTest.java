package io.github.leontecristian.workflow.testhost;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import io.github.leontecristian.workflow.core.dto.runtime.*;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowExecutionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.dto.runtime.SandboxStart;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class SandboxRequestDataUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "null", "3", "\"text\"", "{\"a\":1,\"a\":2}", "{broken}", "{} []"})
    void invalidSourceFailsBeforePersistence(String source) {
        var jdbc = mock(JdbcTemplate.class);
        var store = new SandboxRequestStore(jdbc);
        assertThrows(WorkflowExecutionException.class, () -> store.create("test", source, "admin", Instant.EPOCH));
        verifyNoInteractions(jdbc);
    }

    @Test void reservedInputCannotOverrideSavedDataOnStartOrReplay() {
        var engine = mock(WorkflowExecutionService.class);
        var requests = mock(SandboxRequestStore.class);
        var runtime = new ConsoleRuntimeTransaction(engine, mock(WorkflowRuntimeQueryService.class), requests,
                new ConsoleSettings(new MockEnvironment()));
        var command = new SandboxStart(UUID.randomUUID(), 1, 2, 0,
                new StartInputs(Map.of("requestData", WorkflowJsonValue.parse("{}"))));
        assertThrows(WorkflowExecutionException.class, () -> runtime.start(command, null));
        assertThrows(WorkflowExecutionException.class, () -> runtime.replayStart(command, null));
        verifyNoInteractions(engine, requests);
    }
}
