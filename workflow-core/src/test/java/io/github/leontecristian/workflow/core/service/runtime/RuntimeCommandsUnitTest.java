package io.github.leontecristian.workflow.core.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import io.github.leontecristian.workflow.core.dto.definition.document.ExecutableDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Binding;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.ResolvedTask;
import io.github.leontecristian.workflow.core.dto.runtime.ScalarValue;
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
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository/EntityManager doubles. Does not establish SQL or proxy transaction behavior. */
class RuntimeCommandsUnitTest {
    @Test void completeG04SequenceAndReplayKeepExactValuesAndOriginalSnapshots() {
        var em=mock(EntityManager.class); var instances=mock(WorkflowInstanceRepository.class);
        var activities=mock(WorkflowActivityRepository.class); var tasks=mock(WorkflowTaskRepository.class);
        var history=mock(WorkflowHistoryRepository.class); var receipts=mock(WorkflowCommandReceiptRepository.class);
        var rows=mock(WorkflowVariableRepository.class); var definitions=mock(WorkflowDefinitionRuntimeReader.class);
        var catalog=mock(TaskTypeCatalogService.class); var host=mock(RuntimeHostSupport.class);
        var registry=mock(ServiceActionRegistry.class); var action=mock(WorkflowServiceAction.class);
        var json=new RuntimeJson(); var variables=new RuntimeVariables(rows,em,json);
        var service=new WorkflowExecutionService(instances,activities,tasks,history,receipts,em,definitions,catalog,json,host,
                new RuntimeDefinitionPins(definitions,history,json),variables,new RuntimeServiceProgression(registry,variables,json));
        var fields=Map.of("quantity",new ActionField("quantity",VariableValueType.LONG,false,false),"recordedQuantity",new ActionField("recordedQuantity",VariableValueType.LONG,false,false));
        var model=new MinimalWorkflowDefinition(new MinimalWorkflowDefinition.Node("BEGIN","START","Start",null),
                new MinimalWorkflowDefinition.Human("REVIEW","SANDBOX_REVIEW",new MinimalWorkflowDefinition.Assignment("sandbox.singleReviewer"),new MinimalWorkflowDefinition.Outcome("COMPLETE","Complete",false)),
                new MinimalWorkflowDefinition.Node("DONE","END","Done","COMPLETED"),List.of(
                    new MinimalWorkflowDefinition.Transition("beginService","BEGIN","BEFORE",null),new MinimalWorkflowDefinition.Transition("beginReview","BEFORE","REVIEW",null),
                    new MinimalWorkflowDefinition.Transition("finishReview","REVIEW","AFTER","COMPLETE"),new MinimalWorkflowDefinition.Transition("finishService","AFTER","DONE",null)),
                Map.of("quantity",new MinimalWorkflowDefinition.Input(new ActionField("quantity",VariableValueType.LONG,true,false),null)),fields,
                Map.of("quantity",new Binding("startInput","quantity",null)),Map.of(
                    "BEFORE",new MinimalWorkflowDefinition.Service("BEFORE","sandbox.recordQuantity",Map.of("quantity",new Binding("variable","quantity",null)),Map.of("recordedQuantity",new Binding("actionOutput","recordedQuantity",null)),1),
                    "AFTER",new MinimalWorkflowDefinition.Service("AFTER","sandbox.recordQuantity",Map.of("quantity",new Binding("variable","recordedQuantity",null)),Map.of("recordedQuantity",new Binding("actionOutput","recordedQuantity",null)),3)));
        var definition=new ExecutableDefinition(42,40,"sandbox.serviceReview",1,"ab".repeat(32),DefinitionStatus.PUBLISHED,model);
        when(definitions.loadForStart(42)).thenReturn(definition); when(definitions.loadPinned(42)).thenReturn(definition);
        when(catalog.findByCode("SANDBOX_REVIEW")).thenReturn(Optional.of(new TaskTypeView(5,"SANDBOX_REVIEW","Review",null,false,0)));
        var resolver=mock(SingleTaskAssignmentResolver.class); when(host.resolver(anyString())).thenReturn(resolver);
        when(host.resolve(eq(resolver),any())).thenReturn(new SingleAssignmentResult(List.of(new ResolvedTask("review","reviewer")),null));
        var recorder=mock(WorkflowEventRecorder.class); when(host.recorder()).thenReturn(recorder);
        when(registry.descriptor("sandbox.recordQuantity")).thenReturn(new ServiceActionDescriptor("sandbox.recordQuantity",List.of(new ActionField("quantity",VariableValueType.LONG,true,false)),List.of(new ActionField("recordedQuantity",VariableValueType.LONG,true,false))));
        when(registry.action(anyString())).thenReturn(action);
        when(action.execute(any(),any())).thenAnswer(i -> new ServiceActionResult(Map.of("recordedQuantity",((Map<String,ScalarValue>)i.getArgument(1)).get("quantity"))));
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
        assertEquals(List.of("INSTANCE_STARTED","START_INPUTS_ACCEPTED","VARIABLE_WRITTEN","TRANSITION_TAKEN","SERVICE_TASK_COMPLETED","VARIABLE_WRITTEN","TRANSITION_TAKEN","ACTIVITY_ENTERED","TASK_CREATED"),facts.values().stream().map(WorkflowHistoryEntity::getEventType).toList());
        var instance=persisted.stream().filter(WorkflowInstanceEntity.class::isInstance).map(WorkflowInstanceEntity.class::cast).findFirst().orElseThrow();
        var activity=persisted.stream().filter(WorkflowActivityEntity.class::isInstance).map(WorkflowActivityEntity.class::cast).findFirst().orElseThrow();
        var task=persisted.stream().filter(WorkflowTaskEntity.class::isInstance).map(WorkflowTaskEntity.class::cast).findFirst().orElseThrow();
        when(instances.findById(1L)).thenReturn(Optional.of(instance)); when(activities.findActiveByInstanceId(1L)).thenReturn(Optional.of(activity)); when(tasks.findById(3L)).thenReturn(Optional.of(task));
        when(tasks.findPageByActivityId(eq(2L),eq(0L),any())).thenReturn(List.of(task));
        var complete=new CompleteWorkflowTask(UUID.randomUUID(),1,3,opened.execution().instance().lockVersion(),"COMPLETE",null);
        var reviewer=new WorkflowCommandContext(ActorKind.USER,"reviewer",Instant.parse("2026-01-02T00:00:00Z"),"test");
        clearInvocations(em,action); var closed=service.completeTask(complete,reviewer);
        assertEquals(16,closed.execution().lastHistorySequence()); assertEquals(InstanceStatus.COMPLETED,closed.execution().instance().status());
        assertEquals(2,closed.execution().instance().lockVersion());
        assertEquals(List.of("TASK_COMPLETED","ACTIVITY_COMPLETED","TRANSITION_TAKEN","SERVICE_TASK_COMPLETED","VARIABLE_WRITTEN","TRANSITION_TAKEN","INSTANCE_COMPLETED"),facts.tailMap(10L).values().stream().map(WorkflowHistoryEntity::getEventType).toList());
        var order=inOrder(em,action); order.verify(em).refresh(instance); order.verify(em).flush(); order.verify(action).execute(any(),any());
        for(var row:variableRows.values()) assertEquals("9007199254740993",((TextLiteral)row.scalar().value()).value());
        clearInvocations(action,definitions,em,host);
        assertEquals(opened.execution(),service.start(start,inputs,context,null).execution());
        assertTrue(service.completeTask(complete,reviewer).replayed());
        assertThrows(RuntimeException.class,() -> service.start(start,new StartInputs(Map.of("quantity",new TextLiteral("1"))),context,null));
        verifyNoInteractions(action,definitions,em,host);
        assertEquals(2,storedReceipts.size()); assertTrue(storedReceipts.values().stream().allMatch(r -> r.getResultSchemaVersion()==2));
    }
}
