package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
class ConsoleRuntimeAdaptersUnitTest {
    @Test void cancellationPolicyRequiresBusinessLinkAndAdministratorPermission() {
        var context = mock(ConsoleContext.class);
        var requests = mock(io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore.class);
        var policy = new io.github.leontecristian.workflow.testhost.service.runtime.ConsoleWorkflowCancellationAuthorization(context, requests);
        var commandContext = new WorkflowCommandContext(io.github.leontecristian.workflow.core.dto.runtime.ActorKind.USER,
                "reviewer", java.time.Instant.EPOCH, null);
        var authorization = new io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationAuthorizationContext(
                1, 2, "SANDBOX_REQUEST", "3", commandContext);
        when(context.isConsoleActor("reviewer")).thenReturn(true);
        when(requests.linkedToInstance(1)).thenReturn(true);
        assertThat(policy.canAccess(authorization)).isTrue();
        assertThat(policy.canCancel(authorization)).isFalse();
        when(context.admin()).thenReturn(true);
        assertThat(policy.canCancel(authorization)).isTrue();
        when(requests.linkedToInstance(1)).thenReturn(false);
        assertThat(policy.canAccess(authorization)).isFalse();
    }
    @Test void runtimeResolverKeepsScopedConsoleFailuresAndRecognizesWrappedConflict() throws Exception {
        var resolver = new io.github.leontecristian.workflow.testhost.service.console.ConsoleRuntimeConflictResolver(
                new io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeOptions("/console/api", "trusted"));
        var controller = mock(io.github.leontecristian.workflow.web.controller.runtime.WorkflowRuntimeCommandController.class);
        var handler = new org.springframework.web.method.HandlerMethod(controller, "replayComplete",
                jakarta.servlet.http.HttpServletRequest.class);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setAttribute("trusted", "server-correlation");
        var conflict = new io.github.leontecristian.workflow.testhost.exception.console.SandboxRequestConflict();
        var wrapped = new IllegalStateException("private action details", conflict);
        for (var failure : List.of(conflict, wrapped)) {
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            assertThat(resolver.resolveException(request, response, handler, failure)).isNotNull();
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString()))
                    .isEqualTo(new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                            {"code":"SANDBOX_REQUEST_CONFLICT","message":"SANDBOX_REQUEST_CONFLICT",
                             "diagnostics":[],"correlationId":"server-correlation"}
                            """));
        }
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        assertThat(resolver.resolveException(request, response, handler, new ConsoleException(422, "HOST_VALIDATION")))
                .isNotNull();
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getContentAsString()).contains("HOST_VALIDATION");
        assertThat(resolver.resolveException(request, response, handler, new IllegalStateException())).isNull();
        assertThat(resolver.resolveException(request, response, null, wrapped)).isNull();
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
        var instance = mock(io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView.class);
        when(reads.findInstance(1)).thenReturn(Optional.of(instance));
        when(access.canReadInstance("reviewer", instance)).thenReturn(true);
        var host=RuntimeReadFixtures.authorized(reads,access,mock(io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskEligibilityService.class));
        assertThat(host.listInstanceTasks("reviewer",1,0,50)).isEqualTo(new WorkflowTaskPage(List.of(),500L));
        verify(reads,times(10)).listTasks(org.mockito.ArgumentMatchers.eq(1L),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.eq(50));
    }
    @Test void filteredPageRetainsSourceContinuationAndAssignedIdentityIsTrusted() {
        var reads=mock(WorkflowRuntimeQueryService.class);var access=mock(ConsoleRuntimeAccess.class);var context=mock(ConsoleContext.class);
        when(context.actor()).thenReturn("reviewer");var task=mock(WorkflowTaskView.class);
        when(reads.listTasks(1,0,50)).thenReturn(new WorkflowTaskPage(List.of(task),null));
        var instance = mock(io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView.class);
        when(reads.findInstance(1)).thenReturn(Optional.of(instance));
        when(access.canReadInstance("reviewer", instance)).thenReturn(true);
        var host=RuntimeReadFixtures.authorized(reads,access,mock(io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskEligibilityService.class));
        assertThat(host.listInstanceTasks("reviewer",1,0,50)).isEqualTo(new WorkflowTaskPage(List.of(),null));
        verify(access).canReadInstance("reviewer",instance);
        when(reads.listEligibleTasks(org.mockito.ArgumentMatchers.eq("reviewer"),org.mockito.ArgumentMatchers.eq(77L),org.mockito.ArgumentMatchers.eq(1),org.mockito.ArgumentMatchers.any())).thenReturn(new WorkflowTaskPage(List.of(),null));
        host.listMyTasks("reviewer",77,1);verify(reads).listEligibleTasks(org.mockito.ArgumentMatchers.eq("reviewer"),org.mockito.ArgumentMatchers.eq(77L),org.mockito.ArgumentMatchers.eq(1),org.mockito.ArgumentMatchers.any());
        assertThatThrownBy(()->host.listInstances("reviewer",null)).isInstanceOf(io.github.leontecristian.workflow.core.exception.runtime.WorkflowRuntimeReadAccessException.class);
    }
}
