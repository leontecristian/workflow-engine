package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.github.leontecristian.workflow.core.dto.runtime.*;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.dto.sandbox.SandboxRequestView;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.sandbox.SandboxSetRequestStatusAction;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Isolated action policy checks; transaction/SQL behavior is verified separately. */
class SandboxSetRequestStatusUnitTest {
    private final SandboxRequestStore requests = mock(SandboxRequestStore.class);
    private final SandboxSetRequestStatusAction action = new SandboxSetRequestStatusAction(
            requests, new ConsoleSettings(new MockEnvironment()));

    private ServiceActionContext context(String businessType, String businessId) {
        return new ServiceActionContext(UUID.randomUUID(), 9, 1, "SET_STATUS", businessType, businessId,
                new WorkflowCommandContext(ActorKind.USER, "reviewer", Instant.EPOCH, "unit"));
    }
    private Map<String, WorkflowValue> input(String status) {
        return Map.of("status", new WorkflowValue(VariableValueType.STRING, new TextLiteral(status)));
    }
    private void request(String status, Long instance) {
        when(requests.find(7)).thenReturn(new SandboxRequestView(7, "request", status, "admin", Instant.EPOCH, 3, instance, null));
    }

    @Test void permittedTransitionUsesTrustedLinkAndExpectedRequestVersion() {
        request("IN_REVIEW", 9L);
        var result = action.execute(context("SANDBOX_REQUEST", "7"), input("APPROVED"));
        verify(requests).updateStatus(7, 9, 3, "IN_REVIEW", "APPROVED");
        assertThat(result.outputs().get("previousStatus").value()).isEqualTo(new TextLiteral("IN_REVIEW"));
        assertThat(result.outputs().get("status").value()).isEqualTo(new TextLiteral("APPROVED"));
    }

    @Test void sameStatusDoesNotIncrementVersionOrWrite() {
        request("APPROVED", 9L);
        action.execute(context("SANDBOX_REQUEST", "7"), input("APPROVED"));
        verify(requests, never()).updateStatus(anyLong(), anyLong(), anyLong(), anyString(), anyString());
    }

    @Test void unsupportedOrSkippedOrTerminalTransitionsFailWithoutWrites() {
        for (String[] pair : new String[][] {{"DRAFT", "APPROVED"}, {"APPROVED", "REJECTED"},
                {"REJECTED", "IN_REVIEW"}, {"OTHER", "IN_REVIEW"}, {"DRAFT", "in_review"}}) {
            request(pair[0], 9L);
            assertThatThrownBy(() -> action.execute(context("SANDBOX_REQUEST", "7"), input(pair[1])))
                    .isInstanceOf(RuntimeException.class);
        }
        verify(requests, never()).updateStatus(anyLong(), anyLong(), anyLong(), anyString(), anyString());
    }

    @Test void invalidAssociationOrInputsCannotSelectAnotherRequest() {
        request("DRAFT", null);
        assertThatThrownBy(() -> action.execute(context("SANDBOX_REQUEST", "7"), input("IN_REVIEW"))).isInstanceOf(IllegalStateException.class);
        request("DRAFT", 10L);
        assertThatThrownBy(() -> action.execute(context("SANDBOX_REQUEST", "7"), input("IN_REVIEW"))).isInstanceOf(IllegalStateException.class);
        for (String id : new String[] {"07", "-7", "bad", "9223372036854775808"}) {
            assertThatThrownBy(() -> action.execute(context("SANDBOX_REQUEST", id), input("IN_REVIEW"))).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> action.execute(context("OTHER", "7"), input("IN_REVIEW"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> action.execute(context("SANDBOX_REQUEST", "7"), Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> action.execute(context("SANDBOX_REQUEST", "7"), Map.of("status", new WorkflowValue(VariableValueType.LONG, new TextLiteral("1")))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(requests, never()).updateStatus(anyLong(), anyLong(), anyLong(), anyString(), anyString());
    }
}
