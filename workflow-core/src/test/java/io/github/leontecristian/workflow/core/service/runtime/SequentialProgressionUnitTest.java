package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Assignment;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Human;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Node;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Outcome;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Transition;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SequentialProgressionUnitTest {
    @Test void stopsAtEveryReachedHumanAndSelectsOnlyTheCompletedNodesOutcome() {
        var first=new Human("FIRST","REVIEW",new Assignment("resolver"),List.of(new Outcome("APPROVE","Approve",false)));
        var second=new Human("SECOND","REVIEW",new Assignment("resolver"),List.of(new Outcome("APPROVE","Approve",true)));
        var end=new Node("END","END",null,"DONE");
        var model=new MinimalWorkflowDefinition(new Node("BEGIN","START",null,null),Map.of("SECOND",second,"FIRST",first),end,
                List.of(new Transition("finish","SECOND","END","APPROVE"),new Transition("next","FIRST","SECOND","APPROVE"),
                        new Transition("start","BEGIN","FIRST",null)),Map.of(),Map.of(),Map.of(),Map.of(),Map.of("END",end),Set.of());
        var actions=mock(ServiceActionRegistry.class); var variables=mock(RuntimeVariables.class); var json=new RuntimeJson();
        var progression=new RuntimeServiceProgression(actions,variables,json);
        var context=new WorkflowCommandContext(ActorKind.USER,"reviewer",Instant.EPOCH,"unit");
        String[] sources={"BEGIN","FIRST","SECOND"}, destinations={"FIRST","SECOND","END"};
        for(int index=0;index<sources.length;index++) {
            var batch=new RuntimeHistoryBatch(mock(EntityManager.class),json,1,1,UUID.randomUUID(),context);
            var reached=progression.traverse(model,sources[index],null,new ServiceActionContext(UUID.randomUUID(),1,1,sources[index],null,null,context),
                    Map.of(),new RuntimeProgression(),batch,"hash",index==0?null:"APPROVE");
            assertThat(reached.key()).isEqualTo(destinations[index]);
            assertThat(batch.events()).hasSize(1);
            assertThat(batch.events().get(0).sourceNodeKey()).isEqualTo(sources[index]);
        }
        verifyNoInteractions(actions,variables);
    }
}
