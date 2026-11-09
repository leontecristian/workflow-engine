package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.TaskClosureReason;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowSharedAssignmentProvider;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskEligibilityService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskOwnershipAuthorization;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleTaskOwnershipAuthorization;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeCommandHost;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeCoordinator;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import io.github.leontecristian.workflow.testhost.service.runtime.SandboxSharedAssignmentProvider;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/** In-memory fixture and host policy only; no database or external directory. */
class ConsoleSharedAssignmentUnitTest {
    private WorkflowTaskEligibilityService eligibility(WorkflowSharedAssignmentProvider provider) {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("membership", provider);
        return new WorkflowTaskEligibilityService(beans.getBeanProvider(WorkflowSharedAssignmentProvider.class),
                beans.getBeanProvider(WorkflowTaskOwnershipAuthorization.class));
    }
    private WorkflowTaskView task(TaskStatus status, String closedBy) {
        return new WorkflowTaskView(5, 2, 1, 3, "REVIEW", SandboxSharedAssignmentProvider.FINANCE,
                status, status == TaskStatus.COMPLETED ? "APPROVE" : null, Instant.EPOCH, null,
                status == TaskStatus.COMPLETED ? Instant.EPOCH : null, closedBy,
                status == TaskStatus.COMPLETED ? TaskClosureReason.TASK_OUTCOME : null, null);
    }
    @Test void currentMembershipChangesVisibilityButOnlyActualCompleterRetainsHistoricalAccess() {
        var provider = new SandboxSharedAssignmentProvider(Set.of("first", "second"));
        var reads = mock(WorkflowRuntimeQueryService.class);
        var context = mock(ConsoleContext.class);
        when(context.actor()).thenReturn("first");
        var ownership = mock(ConsoleTaskOwnershipAuthorization.class);
        when(ownership.canAccess(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var access = new ConsoleRuntimeAccess(reads, context, eligibility(provider), ownership);
        var open = task(TaskStatus.OPEN, null);
        assertThat(access.canReadTask(open)).isTrue();
        assertThat(access.canCompleteTask(open)).isTrue();
        provider.replaceMembers(SandboxSharedAssignmentProvider.FINANCE, Set.of("second"));
        assertThat(access.canReadTask(open)).isFalse();
        assertThat(access.canCompleteTask(open)).isFalse();
        var completed = task(TaskStatus.COMPLETED, "first");
        assertThat(access.canReadTask(completed)).isTrue();
        assertThat(access.canCompleteTask(completed)).isFalse();
        when(context.actor()).thenReturn("second");
        assertThat(access.canReadTask(completed)).isFalse();
        provider.removeTarget(SandboxSharedAssignmentProvider.FINANCE);
        assertThat(access.canReadTask(open)).isFalse();
        when(context.admin()).thenReturn(true);
        assertThat(access.canReadTask(open)).isTrue();
        assertThat(access.canCompleteTask(open)).isFalse();
    }
    @Test void formerMemberRetainsExactTaskAccessWithoutAHistoryOrCompletionGrant() {
        var provider = new SandboxSharedAssignmentProvider(Set.of("second"));
        var reads = mock(WorkflowRuntimeQueryService.class);
        var context = mock(ConsoleContext.class);
        when(context.actor()).thenReturn("first");
        var ownership = mock(ConsoleTaskOwnershipAuthorization.class);
        var access = new ConsoleRuntimeAccess(reads, context, eligibility(provider), ownership);
        var task = new WorkflowTaskView(5, 2, 1, 3, "REVIEW", SandboxSharedAssignmentProvider.FINANCE,
                TaskStatus.OPEN, null, Instant.EPOCH, null, null, null, null, "first");
        when(ownership.canAccess(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(reads.findTask(5)).thenReturn(Optional.of(task));
        when(reads.findInstance(1)).thenReturn(Optional.of(mock(
                io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView.class)));
        when(reads.listTasks(1, 0, 100)).thenReturn(
                new io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskPage(java.util.List.of(task), null));

        assertThat(access.task(5)).isSameAs(task);
        assertThat(access.canCompleteTask(task)).isFalse();
        assertThatThrownBy(() -> access.instance(1))
                .isInstanceOf(io.github.leontecristian.workflow.testhost.exception.console.ConsoleException.class);
        when(context.actor()).thenReturn("second");
        assertThat(access.task(5)).isSameAs(task);
        assertThat(access.canCompleteTask(task)).isFalse();
        when(context.actor()).thenReturn("first");
        when(ownership.canAccess(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        assertThatThrownBy(() -> access.task(5))
                .isInstanceOf(io.github.leontecristian.workflow.testhost.exception.console.ConsoleException.class);
    }
    @Test void siblingCompletionDetailsRequireExactTaskAccessEvenWithInstanceHistoryAccess() {
        var reads=mock(WorkflowRuntimeQueryService.class);
        var access=mock(ConsoleRuntimeAccess.class);
        var host=new io.github.leontecristian.workflow.testhost.service.runtime.ConsoleViewerAccess(access,reads);
        var summary=mock(io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary.class);
        when(summary.instanceId()).thenReturn(1L);when(summary.eventType()).thenReturn("TASK_COMPLETED");when(summary.taskId()).thenReturn(5L);
        when(reads.findHistoryEvent(1,9)).thenReturn(Optional.of(new io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistoryEvent(summary,"{}")));
        when(access.task(5)).thenThrow(io.github.leontecristian.workflow.testhost.exception.console.ConsoleException.missing());
        var viewer=new io.github.leontecristian.workflow.web.service.instance.WorkflowInstanceViewerService(reads,host,
                new io.github.leontecristian.workflow.web.service.instance.ViewerWire());
        var previous=org.springframework.security.core.context.SecurityContextHolder.getContext();
        var security=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        security.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("second","",java.util.List.of()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(security);
        try {
            assertThatThrownBy(() -> viewer.event(1,9))
                    .isInstanceOfSatisfying(io.github.leontecristian.workflow.web.exception.instance.WorkflowViewerException.class,
                            failure -> assertThat(failure.status()).isEqualTo(404));
            org.mockito.Mockito.verify(access).task(5);
        } finally { org.springframework.security.core.context.SecurityContextHolder.setContext(previous); }
    }
    @Test void postRollbackRecoveryRechecksHostAccessAndNeverLooksUpReceiptAfterDenial() {
        var tx = mock(ConsoleRuntimeTransaction.class);
        var access = mock(ConsoleRuntimeAccess.class);
        var coordinator = new ConsoleRuntimeCoordinator(tx, access);
        var command = new CompleteWorkflowTask(UUID.randomUUID(), 1, 5, 0, "APPROVE", null);
        when(tx.complete(command, null)).thenThrow(new WorkflowExecutionException(WorkflowExecutionException.Category.CONCURRENT_COMMAND));
        when(access.task(5)).thenThrow(io.github.leontecristian.workflow.testhost.exception.console.ConsoleException.missing());
        assertThatThrownBy(() -> coordinator.complete(command, null))
                .isInstanceOf(io.github.leontecristian.workflow.testhost.exception.console.ConsoleException.class);
        org.mockito.Mockito.verify(tx).complete(command, null);
        org.mockito.Mockito.verifyNoMoreInteractions(tx);
    }
    @Test void providerFailureFailsClosedAndOwnReceiptRecoveryDoesNotConsultMembership() {
        var provider = mock(WorkflowSharedAssignmentProvider.class);
        when(provider.isCurrentMember(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("directory secret"));
        var reads = mock(WorkflowRuntimeQueryService.class);
        var context = mock(ConsoleContext.class);
        when(context.actor()).thenReturn("first");
        var ownership = mock(ConsoleTaskOwnershipAuthorization.class);
        when(ownership.canAccess(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var access = new ConsoleRuntimeAccess(reads, context, eligibility(provider), ownership);
        assertThatThrownBy(() -> access.canReadTask(task(TaskStatus.OPEN, null)))
                .isInstanceOfSatisfying(WorkflowExecutionException.class,
                        failure -> assertThat(failure.category()).isEqualTo(WorkflowExecutionException.Category.ASSIGNMENT_LOOKUP_FAILED));
        org.mockito.Mockito.clearInvocations(provider);
        var completed = task(TaskStatus.COMPLETED, "first");
        when(reads.findTask(5)).thenReturn(Optional.of(completed));
        var tx = mock(ConsoleRuntimeTransaction.class);
        var commands = mock(ConsoleRuntimeCoordinator.class);
        var host = new ConsoleRuntimeCommandHost(commands, tx, access, context);
        var command = new CompleteWorkflowTask(UUID.randomUUID(), 1, 5, 0, "APPROVE", null);
        var result = new WorkflowCommandResult(true, null);
        when(tx.replayComplete(command, null)).thenReturn(Optional.of(result));
        assertThat(host.replayComplete(command, null)).containsSame(result);
        verifyNoInteractions(provider, commands);
        when(context.actor()).thenReturn("second");
        org.mockito.Mockito.clearInvocations(tx);
        assertThatThrownBy(() -> host.replayComplete(command, null))
                .isInstanceOf(io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException.class);
        verifyNoInteractions(tx, provider);
    }
}
