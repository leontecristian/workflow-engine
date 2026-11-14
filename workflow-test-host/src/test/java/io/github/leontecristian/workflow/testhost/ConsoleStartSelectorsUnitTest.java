package io.github.leontecristian.workflow.testhost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.runtime.StartInputs;
import io.github.leontecristian.workflow.testhost.controller.runtime.ConsoleRuntimeController;
import io.github.leontecristian.workflow.testhost.dto.runtime.SandboxStart;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxServiceEffectStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeCoordinator;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

/** Transport parsing with isolated collaborators; security proxies are not exercised. */
class ConsoleStartSelectorsUnitTest {
    private static final UUID COMMAND = UUID.fromString("9a839746-ac19-47fe-985b-a7fdd0091a52");
    private final ConsoleRuntimeCoordinator commands = mock(ConsoleRuntimeCoordinator.class);
    private final ConsoleRuntimeTransaction transactions = mock(ConsoleRuntimeTransaction.class);
    private final ConsoleRuntimeController controller = new ConsoleRuntimeController(
            mock(ConsoleRuntimeAccess.class), commands, transactions, mock(ConsoleContext.class),
            new ConsoleWire(), mock(SandboxServiceEffectStore.class));

    @ParameterizedTest
    @ValueSource(strings = {"\"workflowKey\":\"Booking.review_1-v2\"",
            "\"definitionId\":\"9007199254740993\"", "\"definitionVersionId\":\"9223372036854775807\""})
    void startAndReplayPreserveOriginalSelector(String selector) {
        when(transactions.replayStart(any(), isNull())).thenReturn(Optional.empty());
        controller.start(request(selector));
        controller.replayStart(request(selector));
        var start = ArgumentCaptor.forClass(SandboxStart.class);
        var replay = ArgumentCaptor.forClass(SandboxStart.class);
        verify(commands).start(start.capture(), isNull());
        verify(transactions).replayStart(replay.capture(), isNull());
        assertEquals(start.getValue(), replay.getValue());
        var command = start.getValue();
        assertEquals(COMMAND, command.commandId());
        assertEquals(9007199254740993L, command.requestId());
        assertEquals(command.workflowKey(), command.core().workflowKey());
        assertEquals(command.definitionId(), command.core().definitionId());
        assertEquals(command.definitionVersionId(), command.core().definitionVersionId());
        if (selector.startsWith("\"workflowKey\"")) {
            assertEquals("Booking.review_1-v2", command.workflowKey());
            assertNull(command.definitionId());
            assertNull(command.definitionVersionId());
        } else if (selector.startsWith("\"definitionId\"")) {
            assertEquals(9007199254740993L, command.definitionId());
            assertNull(command.workflowKey());
            assertNull(command.definitionVersionId());
        } else {
            assertEquals(Long.MAX_VALUE, command.definitionVersionId());
            assertNull(command.workflowKey());
            assertNull(command.definitionId());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"workflowKey\":null", "\"workflowKey\":1", "\"workflowKey\":true",
            "\"workflowKey\":{}", "\"workflowKey\":[]", "\"workflowKey\":\"\"", "\"workflowKey\":\" \"",
            "\"workflowKey\":\" key\"", "\"workflowKey\":\"key \"", "\"workflowKey\":\"1key\"",
            "\"definitionId\":null", "\"definitionVersionId\":null", "\"definitionId\":1",
            "\"definitionVersionId\":1", "\"definitionId\":\"0\"", "\"definitionId\":\"01\"",
            "\"definitionId\":\"-1\"", "\"definitionId\":\"9223372036854775808\"",
            "\"workflowKey\":\"key\",\"definitionId\":\"1\"",
            "\"workflowKey\":\"key\",\"definitionVersionId\":null",
            "\"definitionId\":\"1\",\"definitionVersionId\":\"1\"",
            "\"workflowKey\":\"key\",\"unknown\":\"value\""})
    void invalidSelectorsFailBeforeStartOrReceiptLookup(String selector) {
        for (boolean replay : new boolean[] {false, true}) {
            var failure = assertThrows(ConsoleException.class, () -> {
                if (replay) controller.replayStart(request(selector));
                else controller.start(request(selector));
            });
            assertEquals(400, failure.status());
            assertEquals("INVALID_INPUT", failure.code());
        }
        verifyNoInteractions(commands, transactions);
    }

    @Test
    void legacyExactVersionConstructorsRetainCoreIdentity() {
        var command = new SandboxStart(COMMAND, 19, 23, 0);
        assertEquals(new SandboxStart(COMMAND, 19, 23, 0, StartInputs.empty()), command);
        assertEquals(19L, command.core().definitionVersionId());
        assertNull(command.core().workflowKey());
        assertNull(command.core().definitionId());
        assertEquals("SANDBOX_REQUEST", command.core().businessType());
        assertEquals("23", command.core().businessId());
    }

    private MockHttpServletRequest request(String selector) {
        var request = new MockHttpServletRequest();
        request.setContentType("application/json");
        request.setContent(("{\"commandId\":\"" + COMMAND + "\",\"requestId\":\"9007199254740993\","
                + "\"expectedRequestVersion\":\"0\"" + (selector.isEmpty() ? "" : "," + selector)
                + ",\"inputs\":{}}").getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
