package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskEligibilityService;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleTaskBusinessAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleTaskReassignmentAuthorization;
import io.github.leontecristian.workflow.testhost.service.runtime.SandboxSharedAssignmentProvider;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ConsoleReassignmentUnitTest {
    private final ConsoleContext context = mock(ConsoleContext.class);
    private final ConsoleTaskBusinessAccess businessAccess = mock(ConsoleTaskBusinessAccess.class);
    private final ConsoleSettings settings = new ConsoleSettings(new MockEnvironment()
            .withProperty("workflow.console.admin.username", "admin")
            .withProperty("workflow.console.reviewer.username", "reviewer"));
    private final ConsoleTaskReassignmentAuthorization authorization = new ConsoleTaskReassignmentAuthorization(businessAccess, context, settings);
    private final WorkflowRuntimeQueryService reads = mock(WorkflowRuntimeQueryService.class);
    private final WorkflowTaskView task = new WorkflowTaskView(5, 2, 1, 3, "REVIEW", SandboxSharedAssignmentProvider.FINANCE,
            TaskStatus.OPEN, null, Instant.EPOCH, null, null, null, null, null);
    private ConsoleRuntimeAccess access() {
        when(context.actor()).thenReturn("admin");
        when(context.admin()).thenReturn(true);
        when(context.isConsoleAdministrator("admin")).thenReturn(true);
        when(context.isConsoleActor("admin")).thenReturn(true);
        when(businessAccess.canAccess(any())).thenReturn(true);
        when(reads.findTask(5)).thenReturn(Optional.of(task));
        return new ConsoleRuntimeAccess(reads, context, mock(WorkflowTaskEligibilityService.class), businessAccess);
    }
    @Test void knownDestinationsAndAdminPermissionAreSeparateFromBusinessAccess() {
        access(); var request = new TaskEligibilityContext(task, "admin");
        assertThat(authorization.canAccess(request) && authorization.canReassign(request)).isTrue();
        assertThat(authorization.isValidTarget(request, new TaskAssignmentTarget(AssignmentTargetKind.USER, "reviewer2"))).isTrue();
        assertThat(authorization.isValidTarget(request, new TaskAssignmentTarget(AssignmentTargetKind.USER, "Reviewer2"))).isFalse();
        assertThat(authorization.isValidTarget(request, SandboxSharedAssignmentProvider.APPROVER)).isTrue();
        when(context.admin()).thenReturn(false);
        assertThat(authorization.canAccess(request)).isTrue();
        assertThat(authorization.canReassign(request)).isFalse();
    }
    @Test void targetLookupPagesFixturesAndRechecksAccessAndPermission() {
        access();
        var directory = new io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeReadHost(authorization, settings);
        var request = new TaskEligibilityContext(task, "admin");
        var first = directory.reassignmentTargets(request, AssignmentTargetKind.USER, "review", null, 1);
        assertThat(first.items()).extracting(item -> item.ref()).containsExactly("reviewer");
        assertThat(first.nextCursor()).isEqualTo("reviewer");
        var next = directory.reassignmentTargets(request, AssignmentTargetKind.USER, "review", first.nextCursor(), 1);
        assertThat(next.items()).extracting(item -> item.ref()).containsExactly("reviewer2");
        assertThat(next.nextCursor()).isNull();
        assertThat(directory.reassignmentTargets(request, AssignmentTargetKind.GROUP, "Finance", null, 20).items())
                .extracting(item -> item.ref()).containsExactly("sandbox.finance");
        when(context.admin()).thenReturn(false);
        when(context.actor()).thenReturn("reviewer");
        when(context.isConsoleActor("admin")).thenReturn(false);
        when(businessAccess.canAccess(any())).thenReturn(false);
        assertThatThrownBy(() -> directory.reassignmentTargets(request, AssignmentTargetKind.USER, null, null, 20))
                .isInstanceOfSatisfying(io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException.class, failure -> assertThat(failure.status()).isEqualTo(403));
    }
    @Test void effectiveTargetReplacesAssignmentDerivedVisibilityWithoutCompletionOverride() {
        var access = access();
        var moved = new WorkflowTaskView(5, 2, 1, 3, "REVIEW", new TaskAssignmentTarget(AssignmentTargetKind.USER, "reviewer2"),
                TaskStatus.OPEN, null, Instant.EPOCH, null, null, null, null, "reviewer2");
        when(context.admin()).thenReturn(false);
        when(context.actor()).thenReturn("reviewer");
        assertThat(access.canReadTask(moved)).isFalse();
        when(context.actor()).thenReturn("reviewer2");
        assertThat(access.canReadTask(moved)).isTrue();
        when(context.actor()).thenReturn("admin");
        when(context.admin()).thenReturn(true);
        assertThat(access.canReadTask(moved)).isTrue();
        assertThat(RuntimeReadFixtures.authorized(reads, access, mock(WorkflowTaskEligibilityService.class)).canCompleteTask("admin", moved)).isFalse();
    }
    @Test void lookupInfrastructureFailureIsSanitizedAndNeverGrantsPermission() {
        access();
        when(businessAccess.canAccess(any())).thenThrow(new IllegalStateException("private directory detail"));
        var directory = new io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeReadHost(authorization, settings);
        assertThatThrownBy(() -> directory.reassignmentTargets(new TaskEligibilityContext(task, "admin"), AssignmentTargetKind.USER, null, null, 20)).isInstanceOfSatisfying(WorkflowExecutionException.class,
                failure -> assertThat(failure.category()).isEqualTo(WorkflowExecutionException.Category.AUTHORIZATION_LOOKUP_FAILED));
    }
}
