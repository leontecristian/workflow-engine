package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class RoutingDefinitionUnitTest {
    private final ObjectMapper json=new ObjectMapper();
    private final MinimalDefinitionLoader loader=new MinimalDefinitionLoader();
    private final DefinitionDocumentCodec codec=new DefinitionDocumentCodec();
    private ObjectNode fixture() throws Exception { return (ObjectNode)json.readTree(getClass().getResourceAsStream("/f008/quantity-routing.json")); }
    private DefinitionDiagnostics check(ObjectNode root) { var d=new DefinitionDiagnostics(); loader.model(codec.ingest(root.toString()),d); return d; }
    @Test void preservesTransitionArraySourceAndMultipleEnds() throws Exception {
        var root=fixture(); var doc=codec.ingest(root.toString()); var d=new DefinitionDiagnostics();
        var model=loader.model(doc,d); assertThat(d.failed()).isFalse();
        assertThat(model.gateways()).containsExactly("ROUTE"); assertThat(model.ends()).containsKeys("HIGH","LOW");
        assertThat(model.transitions()).extracting(t->t.key()).containsExactly("beginRecord","beginReview","finishReview","fallback","high","positive");
        assertThat(codec.ingest(doc.canonical()).canonical()).isEqualTo(doc.canonical());
        assertThat(model.extended()).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"bypass","cycle","disconnected","defaultFalse","defaultNull","doubleDefault","combined","noRoute","gatewayOutcome","unknown","extraHuman","unsafeLater","computedIndex"})
    void rejectsInvalidGraphsRoutesAndEveryUnsafeCondition(String mutation) throws Exception {
        var root=fixture(); var transitions=(ArrayNode)root.get("transitions");
        switch(mutation) {
            case "bypass" -> ((ObjectNode)transitions.get(1)).put("to","ROUTE");
            case "cycle" -> ((ObjectNode)transitions.get(4)).put("to","ROUTE");
            case "disconnected" -> ((ObjectNode)transitions.get(4)).put("to","LOW");
            case "defaultFalse" -> ((ObjectNode)transitions.get(3)).put("default",false);
            case "defaultNull" -> ((ObjectNode)transitions.get(3)).putNull("default");
            case "doubleDefault" -> { var edge=(ObjectNode)transitions.get(4); edge.remove("condition"); edge.put("default",true); }
            case "combined" -> ((ObjectNode)transitions.get(3)).put("condition","true");
            case "noRoute" -> { while(transitions.size()>3) transitions.remove(3); }
            case "gatewayOutcome" -> ((ObjectNode)transitions.get(4)).put("outcome","COMPLETE");
            case "unknown" -> ((ObjectNode)root.path("nodes").get(3)).put("action","not.allowed");
            case "extraHuman" -> ((ArrayNode)root.get("nodes")).add(root.path("nodes").get(2).deepCopy());
            case "unsafeLater" -> ((ObjectNode)transitions.get(5)).put("condition","T(java.lang.Runtime).getRuntime() != null");
            case "computedIndex" -> ((ObjectNode)transitions.get(5)).put("condition","#vars[#vars.quantity] != null");
            default -> throw new IllegalArgumentException();
        }
        assertThat(check(root).failed()).as(mutation).isTrue();
    }
    @Test void permitsDefaultOnlyAndReconvergenceAndLocatesExpressionErrors() throws Exception {
        var root=fixture(); var transitions=(ArrayNode)root.get("transitions");
        transitions.remove(5); transitions.remove(4); ((ObjectNode)transitions.get(3)).put("to","HIGH");
        ((ArrayNode)root.get("nodes")).remove(5);
        assertThat(check(root).failed()).isFalse();
        root=fixture(); transitions=(ArrayNode)root.get("transitions");
        ((ObjectNode)transitions.get(5)).put("condition","#facts.x > 0");
        assertThat(check(root).report().diagnostics()).anyMatch(d->"/transitions/5/condition".equals(d.pointer()) && d.code().equals("WF_DEF_INVALID_EXPRESSION"));
    }
    @Test void validatesNoVariableGatewayAndKeepsOlderSchemaDigests() throws Exception {
        var root=fixture(); root.putArray("variables"); ((ObjectNode)root.get("startPolicy")).remove("initialize");
        ((ObjectNode)root.get("startPolicy")).putArray("inputContract");
        ((ArrayNode)root.get("nodes")).remove(1);
        var transitions=(ArrayNode)root.get("transitions"); transitions.remove(0); ((ObjectNode)transitions.get(0)).put("from","BEGIN");
        ((ObjectNode)transitions.get(3)).put("condition","true"); ((ObjectNode)transitions.get(4)).put("condition","false");
        assertThat(check(root).failed()).isFalse();
        for(String resource:List.of("minimal-v1.schema.json","scalar-service-v1.schema.json")) {
            String actual=new String(getClass().getResourceAsStream("/schemas/workflow-definition/"+resource).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertThat(loader.schemaDigest(resource.startsWith("scalar"))).isEqualTo(DefinitionContentCanonicalizer.hex(DefinitionContentCanonicalizer.hash(actual)));
        }
    }
}
