package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowAuthorizationRequest;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowPermission;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowResource;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowScope;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskEligibilityService;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeCoordinator;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleTaskBusinessAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleWorkflowAuthorization;
import io.github.leontecristian.workflow.testhost.service.runtime.SandboxCompletionPermissions;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Isolated host policy and composition checks. */
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
        var policy = new ConsoleWorkflowAuthorization(context, business, requests, mock(SandboxCompletionPermissions.class));
        var ownershipAccess = taskRequest(WorkflowScope.ACCESS_OWNERSHIP, authorization);
        var reassignmentAccess = taskRequest(WorkflowScope.ACCESS_REASSIGNMENT, authorization);
        var adminRelease = taskRequest(WorkflowScope.ADMIN_RELEASE, authorization);
        var reassign = taskRequest(WorkflowScope.REASSIGN, authorization);
        assertThat(business.canAccess(authorization)).isFalse();
        verifyNoInteractions(requests);
        when(context.isConsoleActor("reviewer")).thenReturn(true);
        assertThat(policy.isAllowed(ownershipAccess)).isFalse();
        assertThat(policy.isAllowed(reassignmentAccess)).isFalse();
        when(requests.linkedToInstance(1)).thenReturn(true);
        assertThat(policy.isAllowed(ownershipAccess)).isTrue();
        assertThat(policy.isAllowed(reassignmentAccess)).isTrue();
        assertThat(policy.isAllowed(adminRelease)).isFalse();
        assertThat(policy.isAllowed(reassign)).isFalse();
        when(context.admin()).thenReturn(true);
        assertThat(policy.isAllowed(adminRelease)).isTrue();
        assertThat(policy.isAllowed(reassign)).isTrue();
        when(requests.linkedToInstance(1)).thenThrow(new IllegalStateException("private"));
        assertThatThrownBy(() -> policy.isAllowed(ownershipAccess)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy.isAllowed(reassignmentAccess)).isInstanceOf(IllegalStateException.class);
    }

    private static WorkflowAuthorizationRequest taskRequest(WorkflowScope scope, TaskEligibilityContext task) {
        return new WorkflowAuthorizationRequest.Task(new WorkflowPermission(WorkflowResource.TASK, scope), task);
    }

    @Test void adminReadBypassesBusinessAssociationButOperationAvailabilityStaysDenied() {
        when(context.isConsoleAdministrator("admin")).thenReturn(true);
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

}
