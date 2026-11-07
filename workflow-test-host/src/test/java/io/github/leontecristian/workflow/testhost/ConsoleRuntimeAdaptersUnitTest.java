package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeCommandHost;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeCoordinator;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeReadHost;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class ConsoleRuntimeAdaptersUnitTest {
    @Test void completionAndReplayCheckAccessBeforeDispatchWithoutRecoveryMutation() {
        var commands=mock(ConsoleRuntimeCoordinator.class); var tx=mock(ConsoleRuntimeTransaction.class);
        var access=mock(ConsoleRuntimeAccess.class); var context=mock(ConsoleContext.class);
        var actor=mock(WorkflowCommandContext.class); when(context.runtime("trusted")).thenReturn(actor);
        var host=new ConsoleRuntimeCommandHost(commands,tx,access,context);
        var command=new CompleteWorkflowTask(UUID.randomUUID(),1,5,9,"APPROVE",null);
        var result=new WorkflowCommandResult(true,null); when(commands.complete(command,actor)).thenReturn(result);
        assertThat(host.complete(command,"trusted")).isSameAs(result);
        verify(access).task(5); verifyNoInteractions(tx); org.mockito.Mockito.clearInvocations(access);
        when(tx.replayComplete(command,actor)).thenReturn(Optional.empty());
        assertThat(host.replayComplete(command,"trusted")).isEmpty();
        var order=inOrder(access,tx);order.verify(access).task(5);order.verify(tx).replayComplete(command,actor);
        verify(commands,times(1)).complete(command,actor);
        when(access.task(5)).thenThrow(ConsoleException.missing());
        assertThatThrownBy(()->host.replayComplete(command,"trusted")).isInstanceOfSatisfying(WorkflowRuntimeException.class,e->assertThat(e.status()).isEqualTo(404));
        verifyNoMoreInteractions(tx);
    }
    @Test void hostFailuresKeepExistingConsoleStatusAndWrappedBusinessConflictMapping() {
        var commands=mock(ConsoleRuntimeCoordinator.class); var tx=mock(ConsoleRuntimeTransaction.class);
        var access=mock(ConsoleRuntimeAccess.class); var context=mock(ConsoleContext.class);
        var host=new ConsoleRuntimeCommandHost(commands,tx,access,context);
        var command=new CompleteWorkflowTask(UUID.randomUUID(),1,5,9,"APPROVE",null);
        org.mockito.Mockito.doThrow(new ConsoleException(422,"HOST_VALIDATION")).when(commands).complete(command,null);
        assertThatThrownBy(()->host.complete(command,"correlation")).isInstanceOfSatisfying(WorkflowRuntimeException.class,
            e->{assertThat(e.status()).isEqualTo(422);assertThat(e.code()).isEqualTo("HOST_VALIDATION");});
        var conflict=new io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException(
            io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.HOST_CONFIGURATION,
            new io.github.leontecristian.workflow.testhost.exception.console.SandboxRequestConflict());
        org.mockito.Mockito.doThrow(conflict).when(commands).complete(command,null);
        org.mockito.Mockito.doThrow(conflict).when(tx).replayComplete(command,null);
        for(Runnable operation:List.<Runnable>of(()->host.complete(command,"correlation"),()->host.replayComplete(command,"correlation")))
            assertThatThrownBy(operation::run).isInstanceOfSatisfying(WorkflowRuntimeException.class,
                e->{assertThat(e.status()).isEqualTo(409);assertThat(e.code()).isEqualTo("SANDBOX_REQUEST_CONFLICT");});
    }
    @Test void accessFilteredInstancePagesStopAtBudgetWithWorkingContinuation() {
        var reads=mock(WorkflowRuntimeQueryService.class);var access=mock(ConsoleRuntimeAccess.class);var context=mock(ConsoleContext.class);
        when(reads.listTasks(org.mockito.ArgumentMatchers.eq(1L),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.eq(50)))
                .thenAnswer(invocation -> {
                    long cursor=invocation.getArgument(1);
                    var rows=new java.util.ArrayList<WorkflowTaskView>();
                    for(long id=cursor+1;id<=cursor+50;id++) {
                        var task=mock(WorkflowTaskView.class);when(task.id()).thenReturn(id);rows.add(task);
                    }
                    return new WorkflowTaskPage(rows,cursor+50);
                });
        var host=new ConsoleRuntimeReadHost(reads,access,context);
        assertThat(host.listTasks(1,0,50)).isEqualTo(new WorkflowTaskPage(List.of(),500L));
        verify(reads,times(10)).listTasks(org.mockito.ArgumentMatchers.eq(1L),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.eq(50));
    }
    @Test void filteredPageRetainsSourceContinuationAndAssignedIdentityIsTrusted() {
        var reads=mock(WorkflowRuntimeQueryService.class);var access=mock(ConsoleRuntimeAccess.class);var context=mock(ConsoleContext.class);
        when(context.actor()).thenReturn("reviewer");var task=mock(WorkflowTaskView.class);
        when(reads.listTasks(1,0,50)).thenReturn(new WorkflowTaskPage(List.of(task),null));
        var host=new ConsoleRuntimeReadHost(reads,access,context);
        assertThat(host.listTasks(1,0,50)).isEqualTo(new WorkflowTaskPage(List.of(),null));
        verify(access).instance(1);
        when(reads.listEligibleTasks(org.mockito.ArgumentMatchers.eq("reviewer"),org.mockito.ArgumentMatchers.eq(77L),org.mockito.ArgumentMatchers.eq(1),org.mockito.ArgumentMatchers.any())).thenReturn(new WorkflowTaskPage(List.of(),null));
        host.listAssignedTasks(77,1);verify(reads).listEligibleTasks(org.mockito.ArgumentMatchers.eq("reviewer"),org.mockito.ArgumentMatchers.eq(77L),org.mockito.ArgumentMatchers.eq(1),org.mockito.ArgumentMatchers.any());
        assertThatThrownBy(()->host.listInstances(null)).isInstanceOfSatisfying(WorkflowRuntimeException.class,e->assertThat(e.status()).isEqualTo(403));
    }
}
