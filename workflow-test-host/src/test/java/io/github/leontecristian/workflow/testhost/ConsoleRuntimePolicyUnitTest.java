package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskEligibilityService;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeCoordinator;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeReadHost;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleTaskBusinessAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleTaskOwnershipAuthorization;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleTaskReassignmentAuthorization;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Host policy and composition only: no database, clock factory or operation adapter required for reads. */
class ConsoleRuntimePolicyUnitTest {
    private final ConsoleContext context = mock(ConsoleContext.class);
    private final SandboxRequestStore requests = mock(SandboxRequestStore.class);
    private final ConsoleTaskBusinessAccess business = new ConsoleTaskBusinessAccess(context, requests);
    private final WorkflowRuntimeQueryService reads = mock(WorkflowRuntimeQueryService.class);
    private final WorkflowTaskEligibilityService eligibility = mock(WorkflowTaskEligibilityService.class);
    private final ConsoleRuntimeAccess access = new ConsoleRuntimeAccess(reads, context, eligibility, business);
    private final WorkflowTaskView task = new WorkflowTaskView(5, 2, 1, 3, "REVIEW",
            new TaskAssignmentTarget(AssignmentTargetKind.USER, "reviewer"), TaskStatus.OPEN,
            null, Instant.EPOCH, null, null, null, null, "reviewer");

    @Test void sharedBusinessCheckKeepsAuthenticationAssociationAndLookupFailure() {
        var authorization = new TaskEligibilityContext(task, "reviewer");
        var ownership = new ConsoleTaskOwnershipAuthorization(context, business);
        var reassignment = new ConsoleTaskReassignmentAuthorization(business, context, mock(ConsoleSettings.class));
        assertThat(business.canAccess(authorization)).isFalse();
        verifyNoInteractions(requests);
        when(context.isConsoleActor("reviewer")).thenReturn(true);
        assertThat(ownership.canAccess(authorization)).isFalse();
        assertThat(reassignment.canAccess(authorization)).isFalse();
        when(requests.linkedToInstance(1)).thenReturn(true);
        assertThat(ownership.canAccess(authorization)).isTrue();
        assertThat(reassignment.canAccess(authorization)).isTrue();
        assertThat(ownership.canAdministrativelyRelease(authorization)).isFalse();
        assertThat(reassignment.canReassign(authorization)).isFalse();
        when(context.admin()).thenReturn(true);
        assertThat(ownership.canAdministrativelyRelease(authorization)).isTrue();
        assertThat(reassignment.canReassign(authorization)).isTrue();
        when(requests.linkedToInstance(1)).thenThrow(new IllegalStateException("private"));
        assertThatThrownBy(() -> ownership.canAccess(authorization)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reassignment.canAccess(authorization)).isInstanceOf(IllegalStateException.class);
    }

    @Test void adminReadBypassesBusinessAssociationButOperationAvailabilityStaysDenied() {
        when(context.actor()).thenReturn("admin");
        when(context.admin()).thenReturn(true);
        var instance = mock(io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView.class);
        var summary = mock(io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceSummary.class);
        var activity = mock(io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView.class);
        assertThat(access.canReadTask(new TaskEligibilityContext(task, "admin"))).isTrue();
        assertThat(access.canReadInstance("admin", instance)).isTrue();
        assertThat(access.canListInstance("admin", summary)).isTrue();
        assertThat(access.canReadActivity("admin", activity)).isTrue();
        assertThat(access.canMonitor("admin")).isTrue();
        assertThat(access.canAdministerTasks("admin")).isTrue();
        var authorized = RuntimeReadFixtures.authorized(reads, access, eligibility);
        assertThat(authorized.canCompleteTask("admin", task)).isFalse();
        assertThat(authorized.canClaimTask("admin", task)).isFalse();
        assertThat(authorized.canReassignTask("admin", task)).isFalse();
        verifyNoInteractions(requests, reads);
    }

    @Test void ordinaryOpenTaskReadRetainsBusinessDenialAndSanitizedFailure() {
        when(context.actor()).thenReturn("reviewer");
        when(context.isConsoleActor("reviewer")).thenReturn(true);
        var authorization = new TaskEligibilityContext(task, "reviewer");
        assertThat(access.canReadTask(authorization)).isFalse();
        when(requests.linkedToInstance(1)).thenReturn(true);
        assertThat(access.canReadTask(authorization)).isTrue();
        verifyNoInteractions(reads);
        when(requests.linkedToInstance(1)).thenThrow(new IllegalStateException("private"));
        assertThatThrownBy(() -> access.canReadTask(authorization)).isInstanceOfSatisfying(WorkflowExecutionException.class,
                failure -> assertThat(failure.category()).isEqualTo(WorkflowExecutionException.Category.AUTHORIZATION_LOOKUP_FAILED));
    }

    @Test void administratorReadGrantDoesNotWidenCompletionRecoveryAccess() {
        when(context.actor()).thenReturn("admin");
        when(context.admin()).thenReturn(true);
        when(context.isConsoleActor("admin")).thenReturn(true);
        when(reads.findTask(5)).thenReturn(Optional.of(task));
        assertThat(access.task(5)).isSameAs(task);
        assertThatThrownBy(() -> access.completionTask(5)).isInstanceOf(ConsoleException.class);
        var tx = mock(ConsoleRuntimeTransaction.class);
        var command = new CompleteWorkflowTask(UUID.randomUUID(), 1, 5, 9, "APPROVE", null);
        when(tx.complete(command, null)).thenThrow(new WorkflowExecutionException(
                WorkflowExecutionException.Category.CONCURRENT_COMMAND));
        assertThatThrownBy(() -> new ConsoleRuntimeCoordinator(tx, access).complete(command, null))
                .isInstanceOf(ConsoleException.class);
        verify(tx).complete(command, null);
        verifyNoMoreInteractions(tx);
        when(requests.linkedToInstance(1)).thenReturn(true);
        assertThat(access.completionTask(5)).isSameAs(task);
    }

    @Test void currentOwnerCompletionGuardAvoidsMembershipLookupAfterBusinessAccess() {
        when(context.actor()).thenReturn("reviewer");
        when(context.isConsoleActor("reviewer")).thenReturn(true);
        when(requests.linkedToInstance(1)).thenReturn(true);
        var shared = new WorkflowTaskView(5, 2, 1, 3, "REVIEW",
                io.github.leontecristian.workflow.testhost.service.runtime.SandboxSharedAssignmentProvider.FINANCE,
                TaskStatus.OPEN, null, Instant.EPOCH, null, null, null, null, "reviewer");
        when(reads.findTask(5)).thenReturn(Optional.of(shared));
        assertThat(access.completionTask(5)).isSameAs(shared);
        verifyNoInteractions(eligibility);
    }

    @Test void identityDoesNotConstructCommandContextUntilCancellationFactoryIsUsed() {
        when(context.actor()).thenReturn("reviewer");
        var adapter = new ConsoleRuntimeReadHost(context, null, null);
        assertThat(adapter.currentActorRef()).isEqualTo("reviewer");
        verify(context).actor();
        verifyNoMoreInteractions(context);
        adapter.currentCancellationContext();
        verify(context).runtime(null);
    }
}
