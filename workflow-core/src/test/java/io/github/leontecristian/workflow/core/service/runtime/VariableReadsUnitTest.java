package io.github.leontecristian.workflow.core.service.runtime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import io.github.leontecristian.workflow.core.dto.definition.document.ExecutableDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.NullLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowVariableView;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowInstanceEntity;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowActivityRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowHistoryRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowInstanceRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowTaskRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VariableReadsUnitTest {
    @Test void declarationsPageInAsciiOrderAndRetainUninitializedVersusTypedNull() {
        var instances=mock(WorkflowInstanceRepository.class); var pins=mock(RuntimeDefinitionPins.class); var variables=mock(RuntimeVariables.class);
        var instance=mock(WorkflowInstanceEntity.class);
        var declarations=new HashMap<String,ActionField>(); for(String key:List.of("a","B","A")) declarations.put(key,new ActionField(key,VariableValueType.LONG,false,true));
        declarations.put("Result", new ActionField("Result", VariableValueType.JSON, false, false));
        var model=new MinimalWorkflowDefinition(null,Map.of(),null,List.of(),Map.of(),declarations,Map.of(),Map.of(),
                Map.of(),java.util.Set.of(),Map.of("Result","review"));
        when(instances.readLockVersion(1)).thenReturn(Optional.of(7L)); when(instances.findById(1L)).thenReturn(Optional.of(instance));
        when(pins.load(instance)).thenReturn(new ExecutableDefinition(42,40,"flow",1,"ab".repeat(32),DefinitionStatus.PUBLISHED,model));
        when(variables.read(1,model)).thenReturn(Map.of("B",new WorkflowValue(VariableValueType.LONG,NullLiteral.INSTANCE),"a",new WorkflowValue(VariableValueType.LONG,new TextLiteral("9007199254740993"))));
        var service=new WorkflowRuntimeQueryService(instances,mock(WorkflowActivityRepository.class),mock(WorkflowTaskRepository.class),mock(WorkflowHistoryRepository.class),pins,new RuntimeJson(),variables,mock(WorkflowTaskEligibilityService.class));
        var page=service.listVariables(1,null,2,null); assertEquals(List.of("A","B"),page.items().stream().map(WorkflowVariableView::key).toList());
        assertFalse(page.items().get(0).initialized()); assertNull(page.items().get(0).value());
        assertTrue(page.items().get(1).initialized()); assertEquals(NullLiteral.INSTANCE,page.items().get(1).value().value());
        assertEquals("B",page.nextAfterKey());
        assertEquals("a",service.listVariables(1,"B",2,7L).items().get(0).key());
        assertThrows(WorkflowExecutionException.class,() -> service.listVariables(1,"B",2,null));
        assertThrows(WorkflowExecutionException.class,() -> service.listVariables(1,"unknown",2,7L));
        assertEquals(Category.STALE_VERSION,assertThrows(WorkflowExecutionException.class,() -> service.listVariables(1,null,2,6L)).category());
        assertThrows(WorkflowExecutionException.class,() -> service.listVariables(1,"Result",2,7L));
        when(variables.read(1,model)).thenReturn(Map.of("Result",
                new WorkflowValue(VariableValueType.JSON, CompleteWorkflowTask.parseResult("{}"))));
        var created = service.listVariables(1,"B",1,7L);
        assertEquals("Result",created.items().get(0).key());
        assertTrue(created.items().get(0).initialized());
        assertEquals("Result",created.nextAfterKey());
        assertEquals("a",service.listVariables(1,created.nextAfterKey(),2,7L).items().get(0).key());
        when(instances.readLockVersion(1)).thenReturn(Optional.of(7L),Optional.of(8L));
        assertEquals(Category.STALE_VERSION,assertThrows(WorkflowExecutionException.class,() -> service.listVariables(1,null,2,null)).category());
    }
}
