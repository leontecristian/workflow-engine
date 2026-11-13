package io.github.leontecristian.workflow.core.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.calls;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import io.github.leontecristian.workflow.core.dto.definition.document.ExecutableDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.TaskResultContract;
import io.github.leontecristian.workflow.core.dto.definition.document.TaskResultContract.Mode;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Binding;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.ClaimWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ReleaseWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TaskClaimPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.ResolvedTask;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionDescriptor;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionResult;
import io.github.leontecristian.workflow.core.dto.runtime.SingleAssignmentResult;
import io.github.leontecristian.workflow.core.dto.runtime.StartInputs;
import io.github.leontecristian.workflow.core.dto.runtime.StartWorkflow;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeView;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowActivityEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowCommandReceiptEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryId;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowInstanceEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowTaskEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowVariableEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowVariableId;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowActivityRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowCommandReceiptRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowHistoryRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowInstanceRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowTaskRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowVariableRepository;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionRuntimeReader;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository/EntityManager doubles. Does not establish SQL or proxy transaction behavior. */
class RuntimeCommandsUnitTest {
    @ParameterizedTest
    @CsvSource({"false,FORBIDDEN,false,", "true,FORBIDDEN,false,",
            "false,OPTIONAL,false,", "true,REQUIRED,true,", "false,OPTIONAL,false,RESULT_REJECTED",
            "false,OPTIONAL,false,RESULT_VALIDATION_FAILED", "false,REQUIRED,false,INVALID_INPUT",
            "false,FORBIDDEN,true,INVALID_INPUT"})
    void completionKeepsExactValuesAndOriginalSnapshotsForUserAndClaimedSharedTask(boolean shared,
            Mode mode, boolean present,
            Category failureCategory) {
        var em=mock(EntityManager.class); var instances=mock(WorkflowInstanceRepository.class);
        var activities=mock(WorkflowActivityRepository.class); var tasks=mock(WorkflowTaskRepository.class);
        var history=mock(WorkflowHistoryRepository.class); var receipts=mock(WorkflowCommandReceiptRepository.class);
        var rows=mock(WorkflowVariableRepository.class); var definitions=mock(WorkflowDefinitionRuntimeReader.class);
        var catalog=mock(TaskTypeCatalogService.class); var host=mock(RuntimeHostSupport.class);
        var registry=mock(ServiceActionRegistry.class); var action=mock(WorkflowServiceAction.class);
        var json=new RuntimeJson(); var variables=new RuntimeVariables(rows,em,json);
        var eligibility=mock(WorkflowTaskEligibilityService.class);
        when(eligibility.isEligible(any(),anyString())).thenReturn(true);
        var validators = new StaticListableBeanFactory();
        var validator = mock(WorkflowTaskSubmissionValidator.class);
        validators.addBean("validator", validator);
        var service=new WorkflowExecutionService(instances,activities,tasks,history,receipts,em,definitions,catalog,json,host,
                new RuntimeDefinitionPins(definitions,history,json),variables,new RuntimeServiceProgression(registry,variables,json),eligibility,
                validators.getBeanProvider(WorkflowTaskSubmissionValidator.class));
        var fields=Map.of("quantity",new ActionField("quantity",VariableValueType.LONG,false,false),"recordedQuantity",new ActionField("recordedQuantity",VariableValueType.LONG,false,false));
        var contract = new TaskResultContract(mode,
                mode == Mode.FORBIDDEN ? null : "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}");
        var model=new MinimalWorkflowDefinition(new MinimalWorkflowDefinition.Node("BEGIN","START","Start",null),
                new MinimalWorkflowDefinition.Human("REVIEW","SANDBOX_REVIEW",new MinimalWorkflowDefinition.Assignment("sandbox.singleReviewer"),List.of(new MinimalWorkflowDefinition.Outcome("COMPLETE","Complete",false)), TaskClaimPolicy.REQUIRED, contract),
                new MinimalWorkflowDefinition.Node("DONE","END","Done","COMPLETED"),List.of(
                    new MinimalWorkflowDefinition.Transition("beginService","BEGIN","BEFORE",null),new MinimalWorkflowDefinition.Transition("beginReview","BEFORE","REVIEW",null),
                    new MinimalWorkflowDefinition.Transition("finishReview","REVIEW","AFTER","COMPLETE"),new MinimalWorkflowDefinition.Transition("finishService","AFTER","DONE",null)),
                Map.of("quantity",new MinimalWorkflowDefinition.Input(new ActionField("quantity",VariableValueType.LONG,true,false),null)),fields,
                Map.of("quantity",new Binding("startInput","quantity",null),"recordedQuantity",new Binding("startInput","quantity",null)),Map.of(
                    "BEFORE",new MinimalWorkflowDefinition.Service("BEFORE","sandbox.recordQuantity",Map.of("quantity",new Binding("variable","quantity",null)),Map.of("quantity",new Binding("actionOutput","recordedQuantity",null),"recordedQuantity",new Binding("actionOutput","recordedQuantity",null)),1),
                    "AFTER",new MinimalWorkflowDefinition.Service("AFTER","sandbox.recordQuantity",Map.of("quantity",new Binding("variable","recordedQuantity",null)),Map.of("quantity",new Binding("actionOutput","recordedQuantity",null),"recordedQuantity",new Binding("actionOutput","recordedQuantity",null)),3)));
        var definition=new ExecutableDefinition(42,40,"sandbox.serviceReview",1,"ab".repeat(32),DefinitionStatus.PUBLISHED,model);
        when(definitions.loadForStart(42)).thenReturn(definition); when(definitions.loadPinned(42)).thenReturn(definition);
        when(catalog.findByCode("SANDBOX_REVIEW")).thenReturn(Optional.of(new TaskTypeView(5,"SANDBOX_REVIEW","Review",null,false,0)));
        var resolver=mock(SingleTaskAssignmentResolver.class); when(host.resolver(anyString())).thenReturn(resolver);
        when(host.resolve(eq(resolver),any())).thenReturn(new SingleAssignmentResult(List.of(new ResolvedTask("review",new TaskAssignmentTarget(shared ? AssignmentTargetKind.GROUP : AssignmentTargetKind.USER, shared ? "finance" : "reviewer"))),null));
        var recorder=mock(WorkflowEventRecorder.class); when(host.recorder()).thenReturn(recorder);
        when(registry.descriptor("sandbox.recordQuantity")).thenReturn(new ServiceActionDescriptor("sandbox.recordQuantity",List.of(new ActionField("quantity",VariableValueType.LONG,true,false)),List.of(new ActionField("recordedQuantity",VariableValueType.LONG,true,false))));
        when(registry.action(anyString())).thenReturn(action);
        when(action.execute(any(),any())).thenAnswer(i -> new ServiceActionResult(Map.of("recordedQuantity",((Map<String,WorkflowValue>)i.getArgument(1)).get("quantity"))));
        var persisted=new ArrayList<Object>(); var variableRows=new HashMap<WorkflowVariableId,WorkflowVariableEntity>();
        var facts=new TreeMap<Long,WorkflowHistoryEntity>(); var storedReceipts=new HashMap<UUID,WorkflowCommandReceiptEntity>();
        doAnswer(i -> {
            Object e=i.getArgument(0); persisted.add(e);
            if(e instanceof WorkflowInstanceEntity) ReflectionTestUtils.setField(e,"id",1L);
            if(e instanceof WorkflowActivityEntity) ReflectionTestUtils.setField(e,"id",2L);
            if(e instanceof WorkflowTaskEntity) ReflectionTestUtils.setField(e,"id",3L);
            if(e instanceof WorkflowVariableEntity row) variableRows.put(row.getId(),row);
            if(e instanceof WorkflowHistoryEntity row) facts.put(row.getId().getSequenceNumber(),row);
            if(e instanceof WorkflowCommandReceiptEntity row) storedReceipts.put(row.getCommandId(),row);
            return null;
        }).when(em).persist(any());
        when(rows.findById(any())).thenAnswer(i -> Optional.ofNullable(variableRows.get(i.getArgument(0))));
        when(rows.findByInstanceId(eq(1L),any())).thenAnswer(i -> new ArrayList<>(variableRows.values()));
        when(receipts.findById(any())).thenAnswer(i -> Optional.ofNullable(storedReceipts.get(i.getArgument(0))));
        when(history.findById(any())).thenAnswer(i -> Optional.ofNullable(facts.get(((WorkflowHistoryId)i.getArgument(0)).getSequenceNumber())));
        when(history.findCompletionByTaskId(3L)).thenReturn(Optional.empty());
        when(history.findCreationByTaskId(3L)).thenAnswer(i -> facts.values().stream().filter(f -> "TASK_CREATED".equals(f.getEventType()) && Long.valueOf(3).equals(f.getTaskId())).findFirst());
        long[] last={-1};
        doAnswer(i -> {
            persisted.stream().filter(WorkflowInstanceEntity.class::isInstance).map(WorkflowInstanceEntity.class::cast).findFirst().ifPresent(row -> {
                if(last[0]!=row.getLastHistorySequence()) {
                    ReflectionTestUtils.setField(row,"lockVersion",row.getLockVersion()==null ? 0L : row.getLockVersion()+1);
                    last[0]=row.getLastHistorySequence();
                }
            }); return null;
        }).when(em).flush();
        var context=new WorkflowCommandContext(ActorKind.USER,"f007.admin",Instant.parse("2026-01-01T00:00:00Z"),"test");
        var start=new StartWorkflow(UUID.randomUUID(),42,null,null,null);
        var inputs=new StartInputs(Map.of("quantity",new TextLiteral("9007199254740993")));
        var opened=service.start(start,inputs,context,null); assertEquals(9,opened.execution().lastHistorySequence());
        assertEquals(8,opened.execution().activity().activationSequence());
        assertEquals(List.of("INSTANCE_STARTED","START_INPUTS_ACCEPTED","VARIABLES_WRITTEN","TRANSITION_TAKEN","SERVICE_TASK_COMPLETED","VARIABLES_WRITTEN","TRANSITION_TAKEN","ACTIVITY_ENTERED","TASK_CREATED"),facts.values().stream().map(WorkflowHistoryEntity::getEventType).toList());
        assertEquals(2,json.tree(facts.get(3L).getDetailsJson()).path("writes").size());
        assertEquals(2,json.tree(facts.get(6L).getDetailsJson()).path("writes").size());
        var instance=persisted.stream().filter(WorkflowInstanceEntity.class::isInstance).map(WorkflowInstanceEntity.class::cast).findFirst().orElseThrow();
        var activity=persisted.stream().filter(WorkflowActivityEntity.class::isInstance).map(WorkflowActivityEntity.class::cast).findFirst().orElseThrow();
        var task=persisted.stream().filter(WorkflowTaskEntity.class::isInstance).map(WorkflowTaskEntity.class::cast).findFirst().orElseThrow();
        when(instances.findById(1L)).thenReturn(Optional.of(instance)); when(activities.findActiveByInstanceId(1L)).thenReturn(Optional.of(activity)); when(tasks.findById(3L)).thenReturn(Optional.of(task));
        when(tasks.findPageByActivityId(eq(2L),eq(0L),any())).thenReturn(List.of(task));
        var reviewer=new WorkflowCommandContext(ActorKind.USER,"reviewer",Instant.parse("2026-01-02T00:00:00Z"),"test");
        long completionToken = opened.execution().instance().lockVersion();
        long addedFacts = 0;
        if (shared) {
            assertEquals(null, opened.execution().task().assigneeRef());
            when(activities.findById(2L)).thenReturn(Optional.of(activity));
            var claim = new ClaimWorkflowTask(UUID.randomUUID(), 1, 3, completionToken);
            var claimed = service.claimTask(claim, reviewer).execution();
            assertEquals("reviewer", claimed.task().assigneeRef());
            assertEquals(claimed.firstHistorySequence(), claimed.lastHistorySequence());
            var release = new ReleaseWorkflowTask(UUID.randomUUID(), 1, 3, claimed.instance().lockVersion());
            when(eligibility.isEligible(any(), anyString())).thenReturn(false);
            var released = service.releaseTask(release, reviewer).execution();
            assertEquals(null, released.task().assigneeRef());
            assertEquals(claimed, service.findReplay(claim, reviewer).orElseThrow());
            assertEquals(released, service.releaseTask(release, reviewer).execution());
            assertEquals(List.of("TASK_CLAIMED", "TASK_RELEASED"), facts.tailMap(10L).values().stream()
                    .map(WorkflowHistoryEntity::getEventType).toList());
            when(eligibility.isEligible(any(), anyString())).thenReturn(true);
            var reclaimed = service.claimTask(new ClaimWorkflowTask(UUID.randomUUID(), 1, 3,
                    released.instance().lockVersion()), reviewer).execution();
            completionToken = reclaimed.instance().lockVersion();
            addedFacts = 3;
        } else {
            assertEquals("reviewer", opened.execution().task().assigneeRef());
        }
        var result = present ? CompleteWorkflowTask.parseResult("{}") : null;
        var complete = new CompleteWorkflowTask(UUID.randomUUID(), 1, 3, completionToken, "COMPLETE", null, result);
        verify(action).execute(argThat(ctx -> ctx.completedActivityId() == null), any());
        clearInvocations(em, action);
        if (failureCategory != null) {
            if (failureCategory == Category.RESULT_REJECTED || failureCategory == Category.RESULT_VALIDATION_FAILED) {
                doThrow(failureCategory == Category.RESULT_REJECTED
                        ? WorkflowExecutionException.rejectResult("")
                        : new IllegalStateException("Private failure detail")).when(validator).validate(any(), any());
            }
            var failure = assertThrows(WorkflowExecutionException.class,
                    () -> service.completeTask(complete, reviewer));
            assertEquals(failureCategory, failure.category());
            assertEquals(9 + addedFacts, facts.lastKey().longValue());
            assertEquals(1 + addedFacts, storedReceipts.size());
            verifyNoInteractions(action);
            if (failureCategory == Category.INVALID_INPUT) verifyNoInteractions(validator);
            return;
        }
        var closed=service.completeTask(complete,reviewer);
        assertEquals(16 + addedFacts,closed.execution().lastHistorySequence()); assertEquals(InstanceStatus.COMPLETED,closed.execution().instance().status());
        assertEquals(2 + addedFacts,closed.execution().instance().lockVersion());
        assertEquals("reviewer", closed.execution().task().assigneeRef());
        assertEquals(List.of("TASK_COMPLETED","ACTIVITY_COMPLETED","TRANSITION_TAKEN","SERVICE_TASK_COMPLETED","VARIABLES_WRITTEN","TRANSITION_TAKEN","INSTANCE_COMPLETED"),facts.tailMap(10L + addedFacts).values().stream().map(WorkflowHistoryEntity::getEventType).toList());
        var order=inOrder(em,action); order.verify(em).refresh(instance); order.verify(em, calls(1)).flush(); order.verify(action).execute(argThat(ctx -> Long.valueOf(2).equals(ctx.completedActivityId())),any());
        assertEquals(present ? 3 : 2, facts.get(10L + addedFacts).getDetailsSchemaVersion().intValue());
        if (mode == Mode.FORBIDDEN) verifyNoInteractions(validator);
        else verify(validator).validate(argThat(ctx -> ctx.activityId() == 2 && ctx.taskId() == 3), eq(result));
        for(var row:variableRows.values()) assertEquals("9007199254740993",((TextLiteral)row.executionValue().value()).value());
        clearInvocations(action,definitions,em,host,validator);
        assertEquals(opened.execution(),service.start(start,inputs,context,null).execution());
        assertTrue(service.completeTask(complete,reviewer).replayed());
        var changedPresence = new CompleteWorkflowTask(complete.commandId(), 1, 3, completionToken, "COMPLETE", null,
                present ? null : CompleteWorkflowTask.parseResult("{}"));
        assertEquals(Category.COMMAND_ID_CONFLICT,
                assertThrows(WorkflowExecutionException.class, () -> service.findReplay(changedPresence, reviewer)).category());
        assertThrows(RuntimeException.class,() -> service.start(start,new StartInputs(Map.of("quantity",new TextLiteral("1"))),context,null));
        verifyNoInteractions(action,definitions,em,host,validator);
        var completionFact = facts.get(10L + addedFacts);
        when(history.findCompletionByTaskId(3L)).thenReturn(Optional.of(completionFact));
        when(activities.findById(2L)).thenReturn(Optional.of(activity));
        when(tasks.findCompletedPage(eq(2L), eq(0L), any())).thenReturn(List.of(task));
        var reads = new WorkflowRuntimeQueryService(instances, activities, tasks, history,
                new RuntimeDefinitionPins(definitions, history, json), json, variables, eligibility);
        assertEquals(result, reads.findTaskSubmission(3L).orElseThrow().result());
        assertEquals(result, reads.listActivitySubmissions(2L).items().get(0).result());
        var lookupPage = PageRequest.of(0, 2);
        assertEquals(Category.SUBMISSION_UNAVAILABLE, assertThrows(WorkflowExecutionException.class,
                () -> reads.resolveSubmission(1L, "MISSING", mode)).category());
        var incomplete = new WorkflowActivityEntity(1L, "PENDING", 1L, 5L, null, activity.getEnteredAt());
        when(activities.findByInstanceIdAndNodeKey(1L, "PENDING", lookupPage)).thenReturn(List.of(incomplete));
        assertEquals(Category.SUBMISSION_UNAVAILABLE, assertThrows(WorkflowExecutionException.class,
                () -> reads.resolveSubmission(1L, "PENDING", mode)).category());
        when(activities.findByInstanceIdAndNodeKey(1L, activity.getNodeKey(), lookupPage)).thenReturn(List.of(activity, activity));
        assertEquals(Category.HOST_CONFIGURATION, assertThrows(WorkflowExecutionException.class,
                () -> reads.resolveSubmission(1L, activity.getNodeKey(), mode)).category());
        when(activities.findByInstanceIdAndNodeKey(1L, activity.getNodeKey(), lookupPage)).thenReturn(List.of(activity));
        if (present) {
            assertEquals(result, reads.resolveSubmission(1L, activity.getNodeKey(), mode));
            ReflectionTestUtils.setField(activity, "outcomeKey", "INCONSISTENT");
            assertEquals(Category.HOST_CONFIGURATION, assertThrows(WorkflowExecutionException.class,
                    () -> reads.resolveSubmission(1L, activity.getNodeKey(), mode)).category());
            ReflectionTestUtils.setField(activity, "outcomeKey", task.getOutcomeKey());
        } else if (mode == Mode.OPTIONAL) {
            assertEquals(Category.SUBMISSION_UNAVAILABLE, assertThrows(WorkflowExecutionException.class,
                    () -> reads.resolveSubmission(1L, activity.getNodeKey(), mode)).category());
        }
        when(tasks.findPageByActivityId(2L, 0L, lookupPage)).thenReturn(List.of(task, task));
        assertEquals(Category.HOST_CONFIGURATION, assertThrows(WorkflowExecutionException.class,
                () -> reads.resolveSubmission(1L, activity.getNodeKey(), mode)).category());
        when(tasks.findPageByActivityId(2L, 0L, lookupPage)).thenReturn(List.of(task));
        if (present) {
            ReflectionTestUtils.setField(completionFact, "detailsSchemaVersion", (short) 2);
            ReflectionTestUtils.setField(completionFact, "detailsJson",
                    "{\"comment\":null,\"inputs\":{},\"outcomeKey\":\"COMPLETE\"}");
            assertThrows(WorkflowExecutionException.class,
                    () -> reads.findTaskSubmission(3L));
            assertEquals(Category.HOST_CONFIGURATION, assertThrows(WorkflowExecutionException.class,
                    () -> reads.resolveSubmission(1L, activity.getNodeKey(), Mode.REQUIRED)).category());
        }
        assertEquals(2 + addedFacts,storedReceipts.size()); assertTrue(storedReceipts.values().stream().allMatch(r -> r.getResultSchemaVersion()==5));
    }
}
