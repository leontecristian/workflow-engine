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
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleTaskOwnershipAuthorization;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleTaskReassignmentAuthorization;
import io.github.leontecristian.workflow.testhost.service.runtime.SandboxSharedAssignmentProvider;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ConsoleReassignmentUnitTest {
    private final ConsoleContext context = mock(ConsoleContext.class);
    private final ConsoleTaskOwnershipAuthorization ownership = mock(ConsoleTaskOwnershipAuthorization.class);
    private final ConsoleSettings settings = new ConsoleSettings(new MockEnvironment()
            .withProperty("workflow.console.admin.username", "admin")
            .withProperty("workflow.console.reviewer.username", "reviewer"));
    private final ConsoleTaskReassignmentAuthorization authorization = new ConsoleTaskReassignmentAuthorization(ownership, context, settings);
    private final WorkflowRuntimeQueryService reads = mock(WorkflowRuntimeQueryService.class);
    private final WorkflowTaskView task = new WorkflowTaskView(5, 2, 1, 3, "REVIEW", SandboxSharedAssignmentProvider.FINANCE,
            TaskStatus.OPEN, null, Instant.EPOCH, null, null, null, null, null);
    private ConsoleRuntimeAccess access() {
        when(context.actor()).thenReturn("admin");
        when(context.admin()).thenReturn(true);
        when(context.isConsoleActor("admin")).thenReturn(true);
        when(ownership.canAccess(any())).thenReturn(true);
        when(reads.findTask(5)).thenReturn(Optional.of(task));
        return new ConsoleRuntimeAccess(reads, context, mock(WorkflowTaskEligibilityService.class), ownership, authorization, settings);
    }
    @Test void knownDestinationsAndAdminPermissionAreSeparateFromBusinessAccess() {
        var access = access(); var request = new TaskEligibilityContext(task, "admin");
        assertThat(access.canReassignTask(task)).isTrue();
        assertThat(authorization.isValidTarget(request, new TaskAssignmentTarget(AssignmentTargetKind.USER, "reviewer2"))).isTrue();
        assertThat(authorization.isValidTarget(request, new TaskAssignmentTarget(AssignmentTargetKind.USER, "Reviewer2"))).isFalse();
        assertThat(authorization.isValidTarget(request, SandboxSharedAssignmentProvider.APPROVER)).isTrue();
        when(context.admin()).thenReturn(false);
        assertThat(authorization.canAccess(request)).isTrue();
        assertThat(authorization.canReassign(request)).isFalse();
    }
    @Test void targetLookupPagesFixturesAndRechecksAccessAndPermission() {
        var access = access();
        var first = access.reassignmentTargets(task, AssignmentTargetKind.USER, "review", null, 1);
        assertThat(first.items()).extracting(item -> item.ref()).containsExactly("reviewer");
        assertThat(first.nextCursor()).isEqualTo("reviewer");
        var next = access.reassignmentTargets(task, AssignmentTargetKind.USER, "review", first.nextCursor(), 1);
        assertThat(next.items()).extracting(item -> item.ref()).containsExactly("reviewer2");
        assertThat(next.nextCursor()).isNull();
        assertThat(access.reassignmentTargets(task, AssignmentTargetKind.GROUP, "Finance", null, 20).items())
                .extracting(item -> item.ref()).containsExactly("sandbox.finance");
        when(context.admin()).thenReturn(false);
        when(context.actor()).thenReturn("reviewer");
        when(reads.findTask(5)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> access.reassignmentTargets(task, AssignmentTargetKind.USER, null, null, 20))
                .isInstanceOfSatisfying(ConsoleException.class, failure -> assertThat(failure.status()).isEqualTo(404));
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
        assertThat(access.canCompleteTask(moved)).isFalse();
    }
    @Test void lookupInfrastructureFailureIsSanitizedAndNeverGrantsPermission() {
        var access = access();
        when(ownership.canAccess(any())).thenThrow(new IllegalStateException("private directory detail"));
        assertThatThrownBy(() -> access.canReassignTask(task)).isInstanceOfSatisfying(WorkflowExecutionException.class,
                failure -> assertThat(failure.category()).isEqualTo(WorkflowExecutionException.Category.AUTHORIZATION_LOOKUP_FAILED));
    }
}
