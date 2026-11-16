package io.github.leontecristian.workflow.core.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TaskClaimPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstancePage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceQuery;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceSummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationQuery;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationPage;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowRuntimeReadAccessException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** Isolated policy/query doubles; no Spring runtime, database, host or command-context factory. */
class AuthorizedRuntimeReadUnitTest {
    private final WorkflowRuntimeQueryService reads = mock(WorkflowRuntimeQueryService.class);
    private final WorkflowRuntimeReadAuthorization policy = mock(WorkflowRuntimeReadAuthorization.class);
    private final WorkflowTaskEligibilityService eligibility = mock(WorkflowTaskEligibilityService.class);
    private final StaticListableBeanFactory adapters = new StaticListableBeanFactory();
    private final WorkflowAuthorizedRuntimeReadService service = new WorkflowAuthorizedRuntimeReadService(
            reads, policy, eligibility, adapters.getBeanProvider(WorkflowTaskReassignmentAuthorization.class),
            adapters.getBeanProvider(WorkflowCancellationAuthorization.class));

    @Test
    void collectionGrantsDenyBeforeQueryValidationOrStorage() {
        assertEquals(WorkflowRuntimeReadAccessException.Access.MONITOR,
                assertThrows(WorkflowRuntimeReadAccessException.class, () -> service.listInstances("alice", null)).access());
        assertEquals(WorkflowRuntimeReadAccessException.Access.ADMINISTRATION,
                assertThrows(WorkflowRuntimeReadAccessException.class, () -> service.listAdministrationTasks("alice", null)).access());
        verifyNoInteractions(reads, eligibility);
    }

    @Test
    void invalidActorFailsBeforePolicyOrQueries() {
        for (String actor : java.util.Arrays.asList(null, " ", "x".repeat(201), "\uD800")) {
            assertEquals(Category.INVALID_INPUT,
                    assertThrows(WorkflowExecutionException.class, () -> service.task(actor, 1)).category());
        }
        verifyNoInteractions(reads, policy, eligibility);
    }

    @Test
    void exactTaskLoadsOnceAndConcealsDenialAndAbsence() {
        var task = task(1, null);
        when(reads.findTask(1)).thenReturn(Optional.of(task));
        when(reads.findTask(2)).thenReturn(Optional.empty());
        assertEquals(Category.NOT_FOUND,
                assertThrows(WorkflowExecutionException.class, () -> service.task("alice", 1)).category());
        assertEquals(Category.NOT_FOUND,
                assertThrows(WorkflowExecutionException.class, () -> service.task("alice", 2)).category());
        when(policy.canReadTask(new TaskEligibilityContext(task, "alice"))).thenReturn(true);
        assertSame(task, service.task("alice", 1));
        verify(policy, org.mockito.Mockito.times(2)).canReadTask(new TaskEligibilityContext(task, "alice"));
        verifyNoInteractions(eligibility);
    }

    @Test
    void readLookupFailureRetainsItsCategoryRatherThanBecomingDenial() {
        var task = task(1, null);
        var failure = new WorkflowExecutionException(Category.ASSIGNMENT_LOOKUP_FAILED);
        when(reads.findTask(1)).thenReturn(Optional.of(task));
        when(policy.canReadTask(any())).thenThrow(failure);
        assertSame(failure, assertThrows(WorkflowExecutionException.class, () -> service.task("alice", 1)));
    }

    @Test
    void monitorFiltersOneMetadataPageAndRetainsSourceContinuation() {
        var query = new WorkflowInstanceQuery(InstanceStatus.ACTIVE, 0, 2);
        var first = summary(1);
        var second = summary(2);
        when(policy.canMonitor("alice")).thenReturn(true);
        when(reads.listInstances(query)).thenReturn(new WorkflowInstancePage(List.of(first, second), 2L));
        when(policy.canListInstance("alice", second)).thenReturn(true);
        assertEquals(new WorkflowInstancePage(List.of(second), 2L), service.listInstances("alice", query));
        verify(reads).listInstances(query);
        verify(reads, never()).findInstance(org.mockito.ArgumentMatchers.anyLong());
        verify(reads, never()).listTasks(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void myTasksUsesReadPolicyWithoutAnOwnershipAdapter() {
        var expected = new WorkflowTaskPage(List.of(), 500L);
        when(reads.listEligibleTasks(eq("alice"), eq(0L), eq(2), any())).thenReturn(expected);
        assertSame(expected, service.listMyTasks("alice", 0, 2));
        ArgumentCaptor<Predicate<WorkflowTaskView>> predicate = ArgumentCaptor.forClass((Class) Predicate.class);
        verify(reads).listEligibleTasks(eq("alice"), eq(0L), eq(2), predicate.capture());
        var task = task(1, "alice");
        when(policy.canReadTask(new TaskEligibilityContext(task, "alice"))).thenReturn(true);
        assertTrue(predicate.getValue().test(task));
        assertFalse(predicate.getValue().test(task(2, null)));
        verifyNoInteractions(eligibility);
    }

    @Test
    void instanceTaskRefillDoesNotSkipAnUnreturnedAuthorizedCandidate() {
        var instance = instance(InstanceStatus.ACTIVE);
        when(reads.findInstance(9)).thenReturn(Optional.of(instance));
        when(policy.canReadInstance("alice", instance)).thenReturn(true);
        var denied = task(1, null);
        var accepted = task(2, null);
        var next = task(3, null);
        var omitted = task(4, null);
        when(reads.listTasks(9, 0, 2)).thenReturn(new WorkflowTaskPage(List.of(denied, accepted), 2L));
        when(reads.listTasks(9, 2, 2)).thenReturn(new WorkflowTaskPage(List.of(next, omitted), null));
        when(policy.canReadTask(any())).thenAnswer(call -> ((TaskEligibilityContext) call.getArgument(0)).task().id() != 1);
        assertEquals(new WorkflowTaskPage(List.of(accepted, next), 3L), service.listInstanceTasks("alice", 9, 0, 2));
        verify(policy, never()).canReadTask(new TaskEligibilityContext(omitted, "alice"));
    }

    @Test
    void deniedInstancePreventsTaskCandidateQueries() {
        when(reads.findInstance(9)).thenReturn(Optional.of(instance(InstanceStatus.ACTIVE)));
        assertEquals(Category.NOT_FOUND,
                assertThrows(WorkflowExecutionException.class, () -> service.listInstanceTasks("alice", 9, 0, 2)).category());
        verify(reads, never()).listTasks(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void instanceTaskScanStopsAtFiveHundredDeniedCandidatesWithNavigableContinuation() {
        var instance = instance(InstanceStatus.ACTIVE);
        when(reads.findInstance(9)).thenReturn(Optional.of(instance));
        when(policy.canReadInstance("alice", instance)).thenReturn(true);
        when(reads.listTasks(eq(9L), org.mockito.ArgumentMatchers.anyLong(), eq(100))).thenAnswer(call -> {
            long cursor = call.getArgument(1);
            var tasks = java.util.stream.LongStream.rangeClosed(cursor + 1, cursor + 100)
                    .mapToObj(id -> task(id, null)).toList();
            return new WorkflowTaskPage(tasks, cursor + 100);
        });
        assertEquals(new WorkflowTaskPage(List.of(), 500L), service.listInstanceTasks("alice", 9, 0, 100));
        verify(reads, org.mockito.Mockito.times(5)).listTasks(eq(9L), org.mockito.ArgumentMatchers.anyLong(), eq(100));
    }

    @Test
    void administrationDelegatesOneCandidatePageWithExactTaskVisibilityPredicate() {
        var query = new WorkflowTaskAdministrationQuery(TaskStatus.OPEN, null, null, null, null, null, 0, 2, null);
        var page = new WorkflowTaskAdministrationPage(List.of(), 2L);
        when(policy.canAdministerTasks("alice")).thenReturn(true);
        when(reads.listAdministrationTasks(eq(query), any())).thenReturn(page);
        assertSame(page, service.listAdministrationTasks("alice", query));
        verify(reads).listAdministrationTasks(eq(query), any());
        verify(reads, never()).listTasks(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt());
        verifyNoInteractions(eligibility);
    }

    @Test
    void missingOperationsSuppressHintsAndCompletionStillNeedsItsSeparatePermission() {
        var task = task(1, null);
        when(reads.taskClaimPolicy(9, 1)).thenReturn(TaskClaimPolicy.OPTIONAL);
        assertFalse(service.canCompleteTask("alice", task));
        assertFalse(service.canClaimTask("alice", task));
        assertFalse(service.canReassignTask("alice", task));
        assertFalse(service.hasCancellationAuthorization());
        assertFalse(service.canCancelWorkflow("alice", instance(InstanceStatus.ACTIVE), null));
        when(policy.canCompleteTask(new TaskEligibilityContext(task, "alice"))).thenReturn(true);
        when(eligibility.isEligible(task, "alice")).thenReturn(true);
        assertTrue(service.canCompleteTask("alice", task));
    }

    @Test
    void ownerReleaseDoesNotRequireCurrentMembershipAndOtherOwnerCannotComplete() {
        var own = task(1, "alice");
        when(eligibility.canAccessOwnership(new TaskEligibilityContext(own, "alice"))).thenReturn(true);
        assertTrue(service.canReleaseTask("alice", own));
        assertFalse(service.canCompleteTask("bob", own));
        verify(eligibility, never()).isEligible(any(), any());
    }

    @Test
    void reassignmentWorksWithoutOwnershipAndWrapsOperationLookupFailure() {
        var authorization = mock(WorkflowTaskReassignmentAuthorization.class);
        adapters.addBean("reassignment", authorization);
        var task = task(1, null);
        when(authorization.canAccess(any())).thenReturn(true);
        when(authorization.canReassign(any())).thenReturn(true);
        assertTrue(service.canReassignTask("alice", task));
        when(authorization.canReassign(any())).thenThrow(new IllegalStateException("private"));
        assertEquals(Category.AUTHORIZATION_LOOKUP_FAILED,
                assertThrows(WorkflowExecutionException.class, () -> service.canReassignTask("alice", task)).category());
        verifyNoInteractions(eligibility);
    }

    @Test
    void cancellationUsesExplicitMatchingContextAndPreservesTrustedActorKind() {
        var authorization = mock(WorkflowCancellationAuthorization.class);
        adapters.addBean("cancellation", authorization);
        assertTrue(service.hasCancellationAuthorization());
        var context = new WorkflowCommandContext(ActorKind.SYSTEM, "alice", Instant.EPOCH, "read");
        when(authorization.canAccess(any())).thenAnswer(call -> {
            var supplied = (io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationAuthorizationContext) call.getArgument(0);
            assertSame(context, supplied.commandContext());
            return true;
        });
        when(authorization.canCancel(any())).thenReturn(true);
        assertTrue(service.canCancelWorkflow("alice", instance(InstanceStatus.ACTIVE), context));
        assertEquals(Category.HOST_CONFIGURATION, assertThrows(WorkflowExecutionException.class,
                () -> service.canCancelWorkflow("bob", instance(InstanceStatus.ACTIVE), context)).category());
        assertFalse(service.canCancelWorkflow("alice", instance(InstanceStatus.COMPLETED), null));
    }

    private static WorkflowTaskView task(long id, String assignee) {
        return new WorkflowTaskView(id, 8, 9, 1, "review", new TaskAssignmentTarget(AssignmentTargetKind.GROUP, "finance"),
                TaskStatus.OPEN, null, Instant.EPOCH, null, null, null, null, assignee);
    }

    private static WorkflowInstanceView instance(InstanceStatus status) {
        return new WorkflowInstanceView(9, 1, "review", 1, "hash", "BUSINESS", "9", "review", status,
                null, Instant.EPOCH, "alice", null, null, null, 1, 0);
    }

    private static WorkflowInstanceSummary summary(long id) {
        return new WorkflowInstanceSummary(id, 1, "review", 1, InstanceStatus.ACTIVE, "review", null,
                "BUSINESS", "9", Instant.EPOCH, "alice", null, 1, 0);
    }
}
