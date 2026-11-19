package io.github.leontecristian.workflow.core.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskCompletionContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowAuthorizationRequest;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationAuthorizationContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowOperation;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowPermission;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowResource;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowScope;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class WorkflowAuthorizationUnitTest {
    @Test
    void absentAndEmptyPolicyBothWireWithZeroOperations() {
        var emptyPolicy = policy(Set.of());
        try (var absent = wiring(); var empty = wiring(emptyPolicy)) {
            absent.refresh();
            empty.refresh();
            for (var operation : WorkflowOperation.values()) {
                assertFalse(absent.getBean(WorkflowAuthorizationSupport.class).supports(operation));
                assertFalse(empty.getBean(WorkflowAuthorizationSupport.class).supports(operation));
            }
        }
        verify(emptyPolicy).supportedOperations();
        verify(emptyPolicy, never()).isAllowed(any());
    }

    @Test
    void operationSupportIsReadOnceAndSnapshotsTheDeclaration() {
        var declaration = EnumSet.of(WorkflowOperation.CLAIM_TASK, WorkflowOperation.RELEASE_TASK);
        var policy = policy(declaration);
        try (var context = wiring(policy)) {
            context.refresh();
            declaration.clear();
            declaration.add(WorkflowOperation.ADMIN_RELEASE_TASK);
            var support = context.getBean(WorkflowAuthorizationSupport.class);
            assertTrue(support.supports(WorkflowOperation.CLAIM_TASK));
            assertTrue(support.supports(WorkflowOperation.RELEASE_TASK));
            assertFalse(support.supports(WorkflowOperation.ADMIN_RELEASE_TASK));
            assertEquals(Category.HOST_CONFIGURATION, assertThrows(WorkflowExecutionException.class,
                    () -> support.requireSupport(WorkflowOperation.ADMIN_RELEASE_TASK)).category());
        }
        verify(policy).supportedOperations();
        verify(policy, never()).isAllowed(any());
    }

    @Test
    void ambiguousAndInvalidDeclarationsFailSpringRefresh() {
        var unreadable = mock(WorkflowAuthorization.class);
        when(unreadable.supportedOperations()).thenThrow(new IllegalStateException("unavailable"));
        var first = policy(Set.of(WorkflowOperation.CLAIM_TASK));
        var second = policy(Set.of(WorkflowOperation.RELEASE_TASK));
        assertInvalidWiring(first, second);
        verify(first, never()).supportedOperations();
        verify(second, never()).supportedOperations();
        assertInvalidWiring(policy(null));
        assertInvalidWiring(policy(Collections.singleton(null)));
        assertInvalidWiring(policy(Set.of(WorkflowOperation.START_WORKFLOW)));
        assertInvalidWiring(unreadable);
    }

    @Test
    void permissionAndRequestConstructorsRejectIncompatibleResourcesScopesAndContexts() {
        var task = mock(WorkflowTaskView.class);
        var command = new WorkflowCommandContext(ActorKind.USER, "reviewer", Instant.EPOCH, null);
        var eligibility = new TaskEligibilityContext(task, "reviewer");
        var completion = new TaskCompletionContext(command, task, 1, "REVIEW", "REVIEW", "APPROVE", "ok");
        var cancellation = new WorkflowCancellationAuthorizationContext(2, 1, "BOOKING", "3", command);
        for (var scope : WorkflowScope.values()) {
            boolean instanceScope = scope == WorkflowScope.CANCEL || scope == WorkflowScope.ACCESS_CANCELLATION;
            var invalidResource = instanceScope ? WorkflowResource.TASK : WorkflowResource.INSTANCE;
            assertThrows(IllegalArgumentException.class, () -> new WorkflowPermission(invalidResource, scope));
            var permission = new WorkflowPermission(instanceScope ? WorkflowResource.INSTANCE : WorkflowResource.TASK, scope);
            if (instanceScope || scope == WorkflowScope.COMPLETE) {
                assertThrows(IllegalArgumentException.class, () -> new WorkflowAuthorizationRequest.Task(permission, eligibility));
            } else {
                assertEquals(permission, new WorkflowAuthorizationRequest.Task(permission, eligibility).permission());
            }
            if (scope == WorkflowScope.COMPLETE) {
                assertEquals(permission, new WorkflowAuthorizationRequest.Completion(permission, completion).permission());
            } else {
                assertThrows(IllegalArgumentException.class, () -> new WorkflowAuthorizationRequest.Completion(permission, completion));
            }
            if (instanceScope) {
                assertEquals(permission, new WorkflowAuthorizationRequest.Cancellation(permission, cancellation).permission());
            } else {
                assertThrows(IllegalArgumentException.class, () -> new WorkflowAuthorizationRequest.Cancellation(permission, cancellation));
            }
        }
    }

    private static WorkflowAuthorization policy(Set<WorkflowOperation> operations) {
        var policy = mock(WorkflowAuthorization.class);
        when(policy.supportedOperations()).thenReturn(operations);
        return policy;
    }

    private static AnnotationConfigApplicationContext wiring(WorkflowAuthorization... policies) {
        var context = new AnnotationConfigApplicationContext();
        context.register(WorkflowAuthorizationSupport.class);
        for (int index = 0; index < policies.length; index++) {
            var policy = policies[index];
            context.registerBean("policy" + index, WorkflowAuthorization.class, () -> policy);
        }
        return context;
    }

    private static void assertInvalidWiring(WorkflowAuthorization... policies) {
        try (var context = wiring(policies)) {
            Throwable cause = assertThrows(BeanCreationException.class, context::refresh);
            while (cause != null && !(cause instanceof WorkflowExecutionException)) {
                cause = cause.getCause();
            }
            assertNotNull(cause);
            assertEquals(Category.HOST_CONFIGURATION, ((WorkflowExecutionException) cause).category());
        }
        for (var policy : policies) verify(policy, never()).isAllowed(any());
    }
}
