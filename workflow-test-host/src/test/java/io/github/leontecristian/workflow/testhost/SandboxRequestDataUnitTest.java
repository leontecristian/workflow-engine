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
    @ValueSource(strings = {"key", "family", "exact"})
    void startAndReplayPassSelectorAndSavedDataUnchanged(String selector) {
        var engine = mock(WorkflowExecutionService.class);
        var requests = mock(SandboxRequestStore.class);
        var runtime = new ConsoleRuntimeTransaction(engine, mock(WorkflowRuntimeQueryService.class), requests,
                new ConsoleSettings(new MockEnvironment()));
        var command = new SandboxStart(UUID.randomUUID(), selector.equals("key") ? "Booking.review" : null,
                selector.equals("family") ? 9007199254740993L : null,
                selector.equals("exact") ? 9007199254740995L : null, 2, 0,
                new StartInputs(Map.of("approved", new BooleanLiteral(true))));
        when(requests.find(2)).thenReturn(new io.github.leontecristian.workflow.testhost.dto.sandbox.SandboxRequestView(
                2, "test", "DRAFT", "admin", Instant.EPOCH, 0, null, "{\"amount\":1.00}"));
        var expectedInputs = new StartInputs(Map.of("approved", new BooleanLiteral(true),
                "requestData", WorkflowJsonValue.parse("{\"amount\":1.00}")));
        var context = new WorkflowCommandContext(ActorKind.USER, "admin", Instant.EPOCH, "test");
        var result = new WorkflowCommandResult(false, null);
        when(engine.start(eq(command.core()), eq(expectedInputs), eq(context), any())).thenReturn(result);
        when(engine.findReplay(command.core(), expectedInputs, context)).thenReturn(java.util.Optional.of(result));
        org.junit.jupiter.api.Assertions.assertSame(result, runtime.start(command, context));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Optional.of(result), runtime.replayStart(command, context));
        verify(engine).start(eq(command.core()), eq(expectedInputs), eq(context), any());
        verify(engine).findReplay(command.core(), expectedInputs, context);
        verifyNoMoreInteractions(engine);
        verify(requests, times(2)).find(2);
        verifyNoMoreInteractions(requests);
    }

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
