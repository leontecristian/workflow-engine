package io.github.leontecristian.workflow.core.service.definition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionDescriptor;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.service.runtime.ServiceActionRegistry;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowServiceAction;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ScalarDefinitionUnitTest {
    private final DefinitionDocumentCodec codec=new DefinitionDocumentCodec();
    private final MinimalDefinitionLoader loader=new MinimalDefinitionLoader();
    private ObjectNode fixture() throws Exception {
        return (ObjectNode)new ObjectMapper().readTree(getClass().getResourceAsStream("/f007/service-review.json"));
    }
    private DefinitionDiagnostics validate(ObjectNode source) {
        var d=new DefinitionDiagnostics(); loader.model(codec.ingest(source.toString()),d); return d;
    }
    @Test void completeChainAndArrayOrderAreSupportedWithoutRewritingSource() throws Exception {
        var source=fixture(); var d=new DefinitionDiagnostics(); var doc=codec.ingest(source.toString());
        var model=loader.model(doc,d); assertFalse(d.failed()); assertTrue(model.extended()); assertEquals(2,model.services().size());
        var nodes=(com.fasterxml.jackson.databind.node.ArrayNode)source.get("nodes"); var first=nodes.remove(0); nodes.add(first);
        assertFalse(validate(source).failed());
        assertEquals(doc.canonical(),codec.ingest(doc.canonical()).canonical());
    }
    @Test void invalidDefaultNullabilityUnknownFieldsAndUnsupportedSemanticsFail() throws Exception {
        for(String mutation:List.of("requiredDefault","unknown","guard","wrongType","duplicateVariable","disconnected","cycle","json")) {
            var source=fixture();
            switch(mutation) {
                case "requiredDefault" -> ((ObjectNode)source.path("startPolicy").path("inputContract").get(0)).put("default","1");
                case "unknown" -> source.put("unknown",true);
                case "guard" -> ((ObjectNode)source.path("startPolicy")).putArray("guards").addObject().put("action","notSupported");
                case "wrongType" -> ((ObjectNode)source.path("variables").get(0)).put("type","BOOLEAN");
                case "duplicateVariable" -> ((com.fasterxml.jackson.databind.node.ArrayNode)source.get("variables")).add(source.path("variables").get(0));
                case "disconnected" -> ((ObjectNode)source.path("transitions").get(0)).put("to","DONE");
                case "cycle" -> ((ObjectNode)source.path("transitions").get(3)).put("to","BEFORE");
                case "json" -> ((ObjectNode)source.path("variables").get(0)).put("type","JSON");
                default -> fail();
            }
            assertTrue(validate(source).failed(),mutation);
        }
        assertThrows(RuntimeException.class,() -> codec.ingest(fixture().toString().replace("\"schemaVersion\":1","\"schemaVersion\":1,\"schemaVersion\":1")));
    }
    @Test void executableRegistrationsGovernBindingsAndLocatedDiagnostics() throws Exception {
        var action=mock(WorkflowServiceAction.class);
        when(action.descriptor()).thenReturn(new ServiceActionDescriptor("sandbox.recordQuantity",List.of(new ActionField("quantity",VariableValueType.LONG,true,false)),
                List.of(new ActionField("recordedQuantity",VariableValueType.LONG,true,false))));
        ObjectProvider<WorkflowServiceAction> provider=mock(ObjectProvider.class); when(provider.iterator()).thenAnswer(i -> List.of(action).iterator());
        var registry=new ServiceActionRegistry(provider); var d=new DefinitionDiagnostics();
        var model=loader.model(codec.ingest(fixture().toString()),d); ServiceBindingValidation.check(model,registry,d); assertFalse(d.failed());
        verify(action,never()).execute(any(),any());
        var source=fixture(); ((ObjectNode)source.path("nodes").get(1).path("inputs").path("quantity")).put("key","unknown");
        d=new DefinitionDiagnostics(); model=loader.model(codec.ingest(source.toString()),d); ServiceBindingValidation.check(model,registry,d);
        assertTrue(d.failed()); assertTrue(d.report().diagnostics().stream().anyMatch(x -> "/nodes/1/inputs/quantity".equals(x.pointer())));
        when(provider.iterator()).thenAnswer(i -> List.of(action,action).iterator()); assertThrows(RuntimeException.class,() -> new ServiceActionRegistry(provider));
    }
}
