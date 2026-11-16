package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leontecristian.workflow.core.dto.runtime.TaskCompletionContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.exception.runtime.TaskCompletionRejectedException;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.dto.sandbox.SandboxRequestView;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleTaskCompletionAuthorization;
import io.github.leontecristian.workflow.testhost.service.runtime.SandboxCompletionPermissions;
import io.github.leontecristian.workflow.testhost.service.runtime.SandboxCompletionPermissions.Resource;
import io.github.leontecristian.workflow.testhost.service.runtime.SandboxCompletionPermissions.Scope;
import io.github.leontecristian.workflow.testhost.service.runtime.SandboxTaskCompletionValidator;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SandboxCompletionUnitTest {
    private SandboxCompletionPermissions permissions() {
        return new SandboxCompletionPermissions(new ConsoleSettings(new MockEnvironment()
                .withProperty("workflow.console.admin.username", "admin")
                .withProperty("workflow.console.reviewer.username", "reviewer")));
    }
    private SandboxRequestView request(String name, String data) {
        return new SandboxRequestView(1, name, "IN_REVIEW", "creator", Instant.EPOCH, 1, 10L, data);
    }

    @Test void contextualPermissionRolesRemainIndependentOfAssignmentAndAdministratorReads() {
        var permissions = permissions();
        var primary = request("primary", null);
        var secondary = request("F028-SECONDARY:fixture", null);
        assertThat(permissions.isAllowed("reviewer", Resource.BOOKING, Scope.DECIDE, primary)).isTrue();
        assertThat(permissions.isAllowed("reviewer", Resource.BOOKING, Scope.VIEW, secondary)).isTrue();
        assertThat(permissions.isAllowed("reviewer", Resource.BOOKING, Scope.DECIDE, secondary)).isFalse();
        assertThat(permissions.isAllowed("reviewer2", Resource.BOOKING, Scope.DECIDE, secondary)).isTrue();
        assertThat(permissions.isAllowed("admin", Resource.BOOKING, Scope.VIEW, primary)).isTrue();
        assertThat(permissions.isAllowed("admin", Resource.BOOKING, Scope.DECIDE, primary)).isFalse();
        assertThat(permissions.isAllowed("unknown", Resource.BOOKING, Scope.VIEW, primary)).isFalse();
    }

    @Test void exactBusinessAssociationAndTrustedIdentityAreRequiredAndProviderFailurePropagates() {
        var requests = mock(SandboxRequestStore.class);
        var identity = mock(ConsoleContext.class);
        var permissions = mock(SandboxCompletionPermissions.class);
        var authorization = new ConsoleTaskCompletionAuthorization(identity, requests, permissions);
        var task = mock(WorkflowTaskView.class);
        when(task.instanceId()).thenReturn(10L);
        var context = new TaskEligibilityContext(task, "reviewer");
        assertThat(authorization.canAccess(context)).isFalse();
        when(identity.isConsoleActor("reviewer")).thenReturn(true);
        when(requests.findByInstance(10)).thenReturn(Optional.empty());
        assertThat(authorization.canAccess(context)).isFalse();
        var request = request("primary", null);
        when(requests.findByInstance(10)).thenReturn(Optional.of(request));
        when(permissions.isAllowed("reviewer", Resource.BOOKING, Scope.VIEW, request)).thenThrow(new IllegalStateException("provider"));
        assertThatThrownBy(() -> authorization.canAccess(context)).isInstanceOf(IllegalStateException.class);
    }

    @Test void businessValidatorLocksEvenWithoutResultAndRetainsStoredBaseline() {
        var requests = mock(SandboxRequestStore.class);
        var validator = new SandboxTaskCompletionValidator(requests, new ObjectMapper());
        var context = mock(TaskCompletionContext.class);
        var task = mock(WorkflowTaskView.class);
        when(context.task()).thenReturn(task);
        when(task.instanceId()).thenReturn(10L);
        when(requests.lockApprovalBasis(10)).thenReturn(request("primary", null));
        validator.validate(context, null);
        verify(requests).lockApprovalBasis(10);
        when(requests.lockApprovalBasis(10)).thenReturn(request("primary",
                "{\"completionFixture\":{\"approvalBasis\":{\"amount\":1},\"currentBasis\":{\"amount\":2}}}"));
        assertThatThrownBy(() -> validator.validate(context, null)).isInstanceOf(TaskCompletionRejectedException.class);
        when(requests.lockApprovalBasis(10)).thenReturn(request("primary",
                "{\"completionFixture\":{\"approvalBasis\":{\"amount\":1},\"currentBasis\":{\"amount\":1}}}"));
        validator.validate(context, null);
    }
}
