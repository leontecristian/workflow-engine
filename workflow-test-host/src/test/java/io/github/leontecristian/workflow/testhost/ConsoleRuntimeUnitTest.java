package io.github.leontecristian.workflow.testhost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.dto.runtime.SandboxStart;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.exception.console.SandboxRequestConflict;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeCoordinator;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import io.github.leontecristian.workflow.testhost.service.sandbox.SandboxStartIdentity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Isolated collaborators only. These tests do not claim proxy rollback or SQL locking evidence. */
class ConsoleRuntimeUnitTest {
    @Test
    void advertisedSandboxCapabilitiesIncludeEveryExampleOutcome() throws Exception {
        var settings = new io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings(
                new org.springframework.mock.env.MockEnvironment().withProperty("workflow.console.reviewer.username", "reviewer"));
        var controller = new io.github.leontecristian.workflow.testhost.controller.console.ConsoleSessionController(
                new io.github.leontecristian.workflow.testhost.service.console.ConsoleWire(), mock(ConsoleContext.class), settings);
        var advertised = new com.fasterxml.jackson.databind.ObjectMapper().readTree(controller.capabilities().getBody()).path("taskTypes").path("SANDBOX_REVIEW");
        assertEquals(java.util.Set.of("COMPLETE", "APPROVE", "REJECT"),
                java.util.stream.StreamSupport.stream(advertised.spliterator(), false).map(com.fasterxml.jackson.databind.JsonNode::asText).collect(java.util.stream.Collectors.toSet()));
    }
    @Test
    void losingCommandUsesOneReplayLookupWithOriginalInputAndDoesNotRetryMutation() {
        var tx = mock(ConsoleRuntimeTransaction.class);
        var coordinator = new ConsoleRuntimeCoordinator(tx, mock(ConsoleRuntimeAccess.class));
        var command = new SandboxStart(UUID.randomUUID(), 1, 2, 0);
        var context =
                new WorkflowCommandContext(ActorKind.USER, "admin", Instant.EPOCH, "correlation");
        var conflict =
                new WorkflowExecutionException(
                        WorkflowExecutionException.Category.HOST_CONFIGURATION,
                        new SandboxRequestConflict());
        var result = new WorkflowCommandResult(true, null);
        when(tx.start(command, context)).thenThrow(conflict);
        when(tx.replayStart(command, context)).thenReturn(Optional.of(result));
        assertSame(result, coordinator.start(command, context));
        var order = inOrder(tx);
        order.verify(tx).start(command, context);
        order.verify(tx).replayStart(command, context);
        verifyNoMoreInteractions(tx);
    }

    @Test
    void replayMissRetainsOriginalFailureAndOrdinaryCallbackFailureDoesNotRecover() {
        var tx = mock(ConsoleRuntimeTransaction.class);
        var coordinator = new ConsoleRuntimeCoordinator(tx, mock(ConsoleRuntimeAccess.class));
        var command = new SandboxStart(UUID.randomUUID(), 1, 2, 0);
        var conflict =
                new WorkflowExecutionException(WorkflowExecutionException.Category.STALE_VERSION);
        when(tx.start(command, null)).thenThrow(conflict);
        when(tx.replayStart(command, null)).thenReturn(Optional.empty());
        assertSame(
                conflict,
                assertThrows(
                        WorkflowExecutionException.class, () -> coordinator.start(command, null)));
        assertFalse(ConsoleRuntimeCoordinator.eligible(new IllegalStateException("callback")));
        assertFalse(ConsoleRuntimeCoordinator.eligible(new WorkflowExecutionException(null)));
    }

    @Test
    void completedStoredAssignmentStillAuthorizesReviewerWhileOtherActorsStayHidden() {
        var reads = mock(WorkflowRuntimeQueryService.class);
        var context = mock(ConsoleContext.class);
        when(context.actor()).thenReturn("reviewer");
        var access = new ConsoleRuntimeAccess(reads, context, mock(io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskEligibilityService.class));
        var task = mock(WorkflowTaskView.class);
        when(task.assignment()).thenReturn(new io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget(io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER, "reviewer"));
        when(task.status()).thenReturn(TaskStatus.COMPLETED);
        var instance = mock(WorkflowInstanceView.class);
        when(reads.findInstance(1)).thenReturn(Optional.of(instance));
        when(reads.listTasks(1, 0, 100)).thenReturn(new WorkflowTaskPage(List.of(task), null));
        assertSame(instance, access.instance(1));
        when(context.actor()).thenReturn("reviewer ");
        assertEquals(404, assertThrows(ConsoleException.class, () -> access.instance(1)).status());
    }

    @Test
    void startIntentDistinguishesRequestAndExpectedVersionWithoutNumericLoss() {
        assertEquals("610e45dbef77ad8cea723cfde1412d42d46deac09994ce5235a18714a48447b6",
                SandboxStartIdentity.intent(5,0).fingerprintSha256());
        var first = SandboxStartIdentity.intent(9007199254740993L, 0);
        assertEquals("wf-test-start-v1", first.fingerprintVersion());
        assertEquals(first, SandboxStartIdentity.intent(9007199254740993L, 0));
        assertNotEquals(first, SandboxStartIdentity.intent(9007199254740992L, 0));
        assertNotEquals(first, SandboxStartIdentity.intent(9007199254740993L, 1));
    }
}
