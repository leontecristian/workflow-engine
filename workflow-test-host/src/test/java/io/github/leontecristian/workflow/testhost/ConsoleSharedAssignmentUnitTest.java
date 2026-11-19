package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.runtime.TaskClosureReason;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowSharedAssignmentProvider;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskEligibilityService;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleTaskBusinessAccess;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleWorkflowAuthorization;
import io.github.leontecristian.workflow.testhost.service.runtime.SandboxSharedAssignmentProvider;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/** In-memory fixture and host policy only; no database or external directory. */
class ConsoleSharedAssignmentUnitTest {
    private WorkflowTaskEligibilityService eligibility(WorkflowSharedAssignmentProvider provider) {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("membership", provider);
        return new WorkflowTaskEligibilityService(beans.getBeanProvider(WorkflowSharedAssignmentProvider.class));
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
        var businessAccess = mock(ConsoleTaskBusinessAccess.class);
        when(businessAccess.canAccess(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var commandPolicy = mock(ConsoleWorkflowAuthorization.class);
        when(commandPolicy.canDecide(any())).thenReturn(true);
        var access = new ConsoleRuntimeAccess(reads, context, eligibility(provider), businessAccess, commandPolicy);
        var open = task(TaskStatus.OPEN, null);
        when(reads.taskClaimPolicy(1, 5)).thenReturn(io.github.leontecristian.workflow.core.dto.runtime.TaskClaimPolicy.OPTIONAL);
        assertThat(access.canReadTask(open)).isTrue();
        assertThat(RuntimeReadFixtures.authorized(reads, access, eligibility(provider)).canCompleteTask(context.actor(), open)).isTrue();
        provider.replaceMembers(SandboxSharedAssignmentProvider.FINANCE, Set.of("second"));
        assertThat(access.canReadTask(open)).isFalse();
        assertThat(RuntimeReadFixtures.authorized(reads, access, eligibility(provider)).canCompleteTask(context.actor(), open)).isFalse();
        var completed = task(TaskStatus.COMPLETED, "first");
        assertThat(access.canReadTask(completed)).isTrue();
        assertThat(RuntimeReadFixtures.authorized(reads, access, eligibility(provider)).canCompleteTask(context.actor(), completed)).isFalse();
        when(context.actor()).thenReturn("second");
        assertThat(access.canReadTask(completed)).isFalse();
        provider.removeTarget(SandboxSharedAssignmentProvider.FINANCE);
        assertThat(access.canReadTask(open)).isFalse();
        when(context.isConsoleAdministrator("second")).thenReturn(true);
        assertThat(access.canReadTask(open)).isTrue();
        assertThat(RuntimeReadFixtures.authorized(reads, access, eligibility(provider)).canCompleteTask(context.actor(), open)).isFalse();
    }
    @Test void formerMemberRetainsExactTaskAccessWithoutAHistoryOrCompletionGrant() {
        var provider = new SandboxSharedAssignmentProvider(Set.of("second"));
        var reads = mock(WorkflowRuntimeQueryService.class);
        var context = mock(ConsoleContext.class);
        when(context.actor()).thenReturn("first");
        var businessAccess = mock(ConsoleTaskBusinessAccess.class);
        var access = new ConsoleRuntimeAccess(reads, context, eligibility(provider), businessAccess, mock(ConsoleWorkflowAuthorization.class));
        var task = new WorkflowTaskView(5, 2, 1, 3, "REVIEW", SandboxSharedAssignmentProvider.FINANCE,
                TaskStatus.OPEN, null, Instant.EPOCH, null, null, null, null, "first");
        when(businessAccess.canAccess(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(reads.findTask(5)).thenReturn(Optional.of(task));
        var instance = mock(io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView.class);
        when(instance.id()).thenReturn(1L);
        when(reads.findInstance(1)).thenReturn(Optional.of(instance));
        when(reads.listTasks(1, 0, 100)).thenReturn(
                new io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskPage(java.util.List.of(task), null));

        assertThat(access.task("first", 5)).isSameAs(task);
        assertThat(RuntimeReadFixtures.authorized(reads, access, eligibility(provider)).canCompleteTask(context.actor(), task)).isFalse();
        assertThatThrownBy(() -> access.instance(1))
                .isInstanceOf(io.github.leontecristian.workflow.testhost.exception.console.ConsoleException.class);
        when(context.actor()).thenReturn("second");
        assertThat(access.task("second", 5)).isSameAs(task);
        assertThat(RuntimeReadFixtures.authorized(reads, access, eligibility(provider)).canCompleteTask(context.actor(), task)).isFalse();
        when(context.actor()).thenReturn("first");
        when(businessAccess.canAccess(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        assertThatThrownBy(() -> access.task("first", 5))
                .isInstanceOf(io.github.leontecristian.workflow.testhost.exception.console.ConsoleException.class);
    }
    @Test void siblingCompletionDetailsRequireExactTaskAccessEvenWithInstanceHistoryAccess() {
        var reads=mock(WorkflowRuntimeQueryService.class);
        var access=mock(ConsoleRuntimeAccess.class);
        var identity=mock(ConsoleContext.class);
        var host=new io.github.leontecristian.workflow.testhost.service.runtime.ConsoleViewerAccess(access,reads,identity);
        var summary=mock(io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary.class);
        when(summary.instanceId()).thenReturn(1L);when(summary.eventType()).thenReturn("TASK_COMPLETED");when(summary.taskId()).thenReturn(5L);
        when(identity.isConsoleAdministrator("second")).thenReturn(true);
        when(reads.findHistoryEvent(eq(1L),eq(9L),any())).thenReturn(Optional.of(new io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistoryEvent(summary,"{}")));
        when(access.task("second", 5)).thenThrow(io.github.leontecristian.workflow.testhost.exception.console.ConsoleException.missing());
        var viewer=new io.github.leontecristian.workflow.web.service.instance.WorkflowInstanceViewerService(reads,host,
                new io.github.leontecristian.workflow.web.service.instance.ViewerWire());
        assertThatThrownBy(() -> viewer.event("second", 1, 9))
                .isInstanceOfSatisfying(io.github.leontecristian.workflow.web.exception.instance.WorkflowViewerException.class,
                        failure -> assertThat(failure.status()).isEqualTo(404));
        org.mockito.Mockito.verify(access, org.mockito.Mockito.times(3)).instance("second", 1);
        org.mockito.Mockito.verify(access).task("second", 5);
    }
    @Test void providerFailureFailsClosed() {
        var provider = mock(WorkflowSharedAssignmentProvider.class);
        when(provider.isCurrentMember(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("directory secret"));
        var reads = mock(WorkflowRuntimeQueryService.class);
        var context = mock(ConsoleContext.class);
        when(context.actor()).thenReturn("first");
        var businessAccess = mock(ConsoleTaskBusinessAccess.class);
        when(businessAccess.canAccess(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var access = new ConsoleRuntimeAccess(reads, context, eligibility(provider), businessAccess, mock(ConsoleWorkflowAuthorization.class));
        assertThatThrownBy(() -> access.canReadTask(task(TaskStatus.OPEN, null)))
                .isInstanceOfSatisfying(WorkflowExecutionException.class,
                        failure -> assertThat(failure.category()).isEqualTo(WorkflowExecutionException.Category.ASSIGNMENT_LOOKUP_FAILED));

    }
}
