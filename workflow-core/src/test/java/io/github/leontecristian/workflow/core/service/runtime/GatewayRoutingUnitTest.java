package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Assignment;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Human;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Node;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Outcome;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Transition;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowEvent;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayRoutingUnitTest {
    private final RuntimeJson json=new RuntimeJson();
    private MinimalWorkflowDefinition model(String high,String positive,boolean fallback) {
        var edges=new ArrayList<Transition>(); edges.add(new Transition("finish","REVIEW","ROUTE","COMPLETE"));
        if(fallback) edges.add(new Transition("fallback","ROUTE","LOW",null,null,true));
        edges.add(new Transition("high","ROUTE","HIGH",null,high,false));
        edges.add(new Transition("positive","ROUTE","LOW",null,positive,false));
        var end=new Node("HIGH","END",null,"HIGH");
        return new MinimalWorkflowDefinition(new Node("BEGIN","START",null,null),
                new Human("REVIEW","REVIEW",new Assignment("resolver"),List.of(new Outcome("COMPLETE","Complete",false))),
                end,edges,Map.of(),Map.of("quantity",new ActionField("quantity",VariableValueType.LONG,false,true)),Map.of(),Map.of(),
                Map.of("HIGH",end,"LOW",new Node("LOW","END",null,"LOW")),Set.of("ROUTE"));
    }
    private List<WorkflowEvent> run(MinimalWorkflowDefinition model,Map<String,WorkflowValue> state) {
        ObjectProvider<WorkflowServiceAction> provider=mock(ObjectProvider.class); when(provider.iterator()).thenAnswer(i->List.<WorkflowServiceAction>of().iterator());
        var progression=new RuntimeServiceProgression(new ServiceActionRegistry(provider),mock(RuntimeVariables.class),json);
        var context=new WorkflowCommandContext(ActorKind.USER,"reviewer",Instant.parse("2026-11-05T00:00:00Z"),"test");
        var batch=new RuntimeHistoryBatch(mock(EntityManager.class),json,1,1,UUID.randomUUID(),context);
        var end=progression.traverse(model,"REVIEW",null,new ServiceActionContext(UUID.randomUUID(),1,1,"REVIEW",null,null,context),
                state,new RuntimeProgression(),batch,"a".repeat(64),"COMPLETE");
        assertThat(end.type()).isEqualTo("END");
        assertThat(end.key()).isEqualTo(batch.events().get(batch.events().size()-1).targetNodeKey());
        return batch.events();
    }
    @Test void firstMatchDefaultOrderAndEvidencePrivacy() {
        for(String quantity:List.of("20","5","0")) {
            var events=run(model("#vars.quantity > 10","#vars.quantity > 0",true),Map.of("quantity",new WorkflowValue(VariableValueType.LONG,new TextLiteral(quantity))));
            assertThat(events).extracting(WorkflowEvent::eventType).containsExactly("TRANSITION_TAKEN","GATEWAY_ROUTED","TRANSITION_TAKEN");
            String selected=quantity.equals("20") ? "high" : quantity.equals("5") ? "positive" : "fallback";
            assertThat(events.get(1).payload()).containsExactlyInAnyOrderEntriesOf(Map.of("nodeKey","ROUTE","selectedTransitionKey",selected));
            assertThat(events.get(2).transitionKey()).isEqualTo(selected);
        }
        assertThat(run(model("true","1 / 0 == 1",true),Map.of())).hasSize(3);
    }
    @Test void noRouteUninitializedAndErrorsNeverDefault() {
        for(var entry:Map.of("false",WorkflowExecutionException.Category.ROUTE_NOT_FOUND,
                "#vars.quantity == null",WorkflowExecutionException.Category.UNINITIALIZED_VARIABLE,
                "1 / 0 > 0",WorkflowExecutionException.Category.EXPRESSION_EVALUATION_FAILED,
                "2 ^ 1025 > 0",WorkflowExecutionException.Category.EXPRESSION_LIMIT_EXCEEDED).entrySet()) {
            boolean fallback=!entry.getKey().equals("false");
            assertThatThrownBy(()->run(model(entry.getKey(),"false",fallback),Map.of())).isInstanceOfSatisfying(WorkflowExecutionException.class,e->assertThat(e.category()).isEqualTo(entry.getValue()));
        }
    }
}
