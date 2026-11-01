package io.github.leontecristian.workflow.core.service.runtime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionDescriptor;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionResult;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowEvent;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServiceProgressionUnitTest {
    private final RuntimeJson json=new RuntimeJson();
    private final EntityManager em=mock(EntityManager.class);
    private final ServiceActionRegistry registry=mock(ServiceActionRegistry.class);
    private final RuntimeVariables variables=mock(RuntimeVariables.class);
    private final WorkflowServiceAction action=mock(WorkflowServiceAction.class);
    private final WorkflowCommandContext context=new WorkflowCommandContext(ActorKind.USER,"admin",Instant.parse("2026-01-01T00:00:00Z"),"test");
    private MinimalWorkflowDefinition model(int count) {
        var services=new HashMap<String,MinimalWorkflowDefinition.Service>(); var transitions=new ArrayList<MinimalWorkflowDefinition.Transition>();
        String previous="START";
        for(int i=0;i<count;i++) {
            String key="S"+i; services.put(key,new MinimalWorkflowDefinition.Service(key,"host.action",Map.of(),Map.of(),i));
            transitions.add(new MinimalWorkflowDefinition.Transition("t"+i,previous,key,null)); previous=key;
        }
        transitions.add(new MinimalWorkflowDefinition.Transition("human",previous,"HUMAN",null));
        return new MinimalWorkflowDefinition(new MinimalWorkflowDefinition.Node("START","START","Start",null),
                new MinimalWorkflowDefinition.Human("HUMAN","TASK",new MinimalWorkflowDefinition.Assignment("resolver"),new MinimalWorkflowDefinition.Outcome("DONE","Done",false)),
                new MinimalWorkflowDefinition.Node("END","END","End","DONE"),transitions,Map.of(),Map.of(),Map.of(),services);
    }
    @Test void step50CanPauseAndStep51IsNeverInvoked() {
        when(registry.descriptor("host.action")).thenReturn(new ServiceActionDescriptor("host.action",List.of(),List.of()));
        when(registry.action("host.action")).thenReturn(action);
        when(action.execute(any(),any())).thenReturn(new ServiceActionResult(Map.of()));
        var progression=new RuntimeServiceProgression(registry,variables,json);
        for(int count:new int[]{49,50}) {
            clearInvocations(action); var model=model(count); var budget=new RuntimeProgression(); budget.visit(model.start(),"START");
            var batch=new RuntimeHistoryBatch(em,json,1,42,UUID.randomUUID(),context);
            Runnable run=() -> progression.traverse(model,"START","HUMAN",new ServiceActionContext(UUID.randomUUID(),1,42,"START",null,null,context),new TreeMap<>(),budget,batch);
            if(count==49) { run.run(); assertEquals(99,batch.last()); }
            else assertEquals(Category.LIMIT_EXCEEDED,assertThrows(WorkflowExecutionException.class,run::run).category());
            verify(action,times(49)).execute(any(),any());
            assertTrue(batch.events().stream().allMatch(e -> e.payload().keySet().stream().noneMatch(k -> k.equals("inputs") || k.equals("outputs"))));
        }
    }
    @Test void invalidOutputsAndActionThrowablesProduceNoSuccessOrVariableWrites() {
        when(registry.descriptor("host.action")).thenReturn(new ServiceActionDescriptor("host.action",List.of(),List.of(new ActionField("required",VariableValueType.LONG,true,false))));
        when(registry.action("host.action")).thenReturn(action);
        for(boolean throwing:new boolean[]{false,true}) {
            when(action.execute(any(),any())).thenAnswer(i -> { if(throwing) throw new IllegalStateException("private"); return new ServiceActionResult(Map.of()); });
            var batch=new RuntimeHistoryBatch(em,json,1,42,UUID.randomUUID(),context); var m=model(1);
            var error=assertThrows(WorkflowExecutionException.class,() -> new RuntimeServiceProgression(registry,variables,json).traverse(m,"START","HUMAN",
                    new ServiceActionContext(UUID.randomUUID(),1,42,"START",null,null,context),new TreeMap<>(),new RuntimeProgression(),batch));
            assertEquals(throwing?Category.SERVICE_ACTION_FAILED:Category.INVALID_ACTION_OUTPUT,error.category());
            assertEquals(List.of("TRANSITION_TAKEN"),batch.events().stream().map(WorkflowEvent::eventType).toList());
            verifyNoInteractions(variables);
        }
    }
    @Test void historyIsBoundedByCompleteEventAndBatchBudgets() {
        var batch=new RuntimeHistoryBatch(em,json,1,42,UUID.randomUUID(),context);
        for(int i=0;i<6000;i++) batch.next("TEST",null,null,null,Map.of(),Map.of());
        assertThrows(WorkflowExecutionException.class,() -> batch.next("TEST",null,null,null,Map.of(),Map.of()));
        var large=new RuntimeHistoryBatch(em,json,1,42,UUID.randomUUID(),context);
        assertThrows(WorkflowExecutionException.class,() -> large.next("TEST",null,null,null,Map.of("value","x".repeat(65536)),Map.of()));
        var total=new RuntimeHistoryBatch(em,json,1,42,UUID.randomUUID(),context);
        assertThrows(WorkflowExecutionException.class,() -> { for(int i=0;i<200;i++) total.next("TEST",null,null,null,Map.of("value","x".repeat(60000)),Map.of()); });
    }
}
