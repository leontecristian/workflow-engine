package io.github.leontecristian.workflow.core.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.CancelWorkflow;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowInstanceEntity;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowActivityRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowCommandReceiptRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowHistoryRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowInstanceRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowTaskRepository;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionRuntimeReader;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** Isolated cancellation boundary ordering; transaction/effect behavior belongs to SQL checks. */
class CancellationCommandUnitTest {
    private final WorkflowInstanceRepository instances = mock(WorkflowInstanceRepository.class);
    private final WorkflowActivityRepository activities = mock(WorkflowActivityRepository.class);
    private final WorkflowTaskRepository tasks = mock(WorkflowTaskRepository.class);
    private final WorkflowHistoryRepository history = mock(WorkflowHistoryRepository.class);
    private final WorkflowCommandReceiptRepository receipts = mock(WorkflowCommandReceiptRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final RuntimeJson json = mock(RuntimeJson.class);
    private final WorkflowCancellationAuthorization authorization = mock(WorkflowCancellationAuthorization.class);
    private final WorkflowCommandContext context = new WorkflowCommandContext(ActorKind.USER, "admin", Instant.EPOCH, "attempt");
    private final CancelWorkflow command = new CancelWorkflow(UUID.randomUUID(), 1, 9, " exact ");

    private WorkflowExecutionService service(boolean supported) {
        var beans = new StaticListableBeanFactory();
        if (supported) beans.addBean("cancellation", authorization);
        return new WorkflowExecutionService(instances, activities, tasks, history, receipts, em,
                mock(WorkflowDefinitionRuntimeReader.class), mock(TaskTypeCatalogService.class), json,
                mock(RuntimeHostSupport.class), mock(RuntimeDefinitionPins.class), mock(RuntimeVariables.class),
                mock(RuntimeServiceProgression.class), mock(WorkflowTaskEligibilityService.class),
                beans.getBeanProvider(WorkflowTaskSubmissionValidator.class), beans.getBeanProvider(WorkflowCancellationAuthorization.class));
    }

    private void target() {
        var instance = mock(WorkflowInstanceEntity.class);
        when(instance.getId()).thenReturn(1L);
        when(instance.getDefinitionVersionId()).thenReturn(2L);
        when(instances.findById(1L)).thenReturn(Optional.of(instance));
        when(receipts.findById(command.commandId())).thenReturn(Optional.empty());
        when(json.cancellationReplay(null, command, context)).thenReturn(Optional.empty());
    }

    private void category(Category expected, Runnable action) {
        assertEquals(expected, assertThrows(WorkflowExecutionException.class, action::run).category());
    }

    @Test void invalidReasonsAndNonUserActorsFailBeforeAnyCollaborator() {
        var service = service(true);
        for (String reason : Arrays.asList(null, "", " \t", "\ud800", "\udc00", "x".repeat(2001))) {
            var invalid = new CancelWorkflow(command.commandId(), 1, 9, reason);
            category(Category.INVALID_INPUT, () -> service.cancelWorkflow(invalid, context));
            category(Category.INVALID_INPUT, () -> service.findCancellationReplay(invalid, context));
        }
        category(Category.INVALID_INPUT, () -> service.cancelWorkflow(null, context));
        category(Category.INVALID_INPUT, () -> service.cancelWorkflow(command,
                new WorkflowCommandContext(ActorKind.SYSTEM, "system", Instant.EPOCH, null)));
        verifyNoInteractions(instances, receipts, em, activities, tasks, history, authorization, json);
    }

    @Test void missingCancellationSupportFailsClosedBeforeTargetLookup() {
        var service = service(false);
        category(Category.HOST_CONFIGURATION, () -> service.cancelWorkflow(command, context));
        category(Category.HOST_CONFIGURATION, () -> service.findCancellationReplay(command, context));
        verifyNoInteractions(instances, receipts, em);
    }

    @Test void accessDenialPrecedesReceiptsAndPermissionAndAdapterFailuresAreSanitized() {
        target();
        var service = service(true);
        category(Category.NOT_FOUND, () -> service.cancelWorkflow(command, context));
        category(Category.NOT_FOUND, () -> service.findCancellationReplay(command, context));
        verifyNoInteractions(receipts, json, activities, tasks, history);
        verify(authorization, never()).canCancel(any());
        when(authorization.canAccess(any())).thenThrow(new IllegalStateException("private"));
        category(Category.AUTHORIZATION_LOOKUP_FAILED, () -> service.cancelWorkflow(command, context));
        category(Category.AUTHORIZATION_LOOKUP_FAILED, () -> service.findCancellationReplay(command, context));
    }

    @Test void freshPermissionDenialPrecedesLifecycleVersionClaimAndEffects() {
        target();
        when(authorization.canAccess(any())).thenReturn(true);
        var effect = mock(WorkflowCancellationEffect.class);
        var service = service(true);
        category(Category.CANCELLATION_FORBIDDEN, () -> service.cancelWorkflow(command, context, effect));
        verifyNoInteractions(activities, tasks, history, effect);
        verify(em, never()).flush();
        verify(instances.findById(1L).orElseThrow(), never()).reserveHistorySequence();
        when(authorization.canCancel(any())).thenThrow(new IllegalStateException("private"));
        category(Category.AUTHORIZATION_LOOKUP_FAILED, () -> service.cancelWorkflow(command, context, effect));
    }

    @Test void lookupOnlyMissNeverChecksFreshPermissionOrMutates() {
        target();
        when(authorization.canAccess(any())).thenReturn(true);
        assertTrue(service(true).findCancellationReplay(command, context).isEmpty());
        verify(authorization, never()).canCancel(any());
        verifyNoInteractions(em, activities, tasks, history);
    }

    @Test void replaySurvivesPrivilegeLossButNeverBusinessAccessLoss() {
        target();
        when(authorization.canAccess(any())).thenReturn(true);
        var retained = mock(WorkflowCancellationResult.class);
        when(json.cancellationReplay(null, command, context)).thenReturn(Optional.of(retained));
        var effect = mock(WorkflowCancellationEffect.class);
        var service = service(true);
        assertSame(retained, service.findCancellationReplay(command, context).orElseThrow());
        var replay = service.cancelWorkflow(command, context, effect);
        assertTrue(replay.replayed());
        assertSame(retained, replay.execution());
        verify(authorization, never()).canCancel(any());
        verifyNoInteractions(effect, activities, tasks, history);
        verify(em, never()).flush();
        when(authorization.canAccess(any())).thenReturn(false);
        category(Category.NOT_FOUND, () -> service.findCancellationReplay(command, context));
        category(Category.NOT_FOUND, () -> service.cancelWorkflow(command, context));
    }
}
