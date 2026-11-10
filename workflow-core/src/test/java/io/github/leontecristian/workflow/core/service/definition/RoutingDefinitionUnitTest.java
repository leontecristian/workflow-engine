package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.runtime.TaskClaimPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionDescriptor;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.definition.document.TaskResultContract;
import io.github.leontecristian.workflow.core.service.runtime.ServiceActionRegistry;
import java.util.List;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test void validatesAndPinsResultContractsAcrossAllThreeDefinitionSchemas() throws Exception {
        var documents = List.of(minimalFixture(),
                (ObjectNode) json.readTree(getClass().getResourceAsStream("/f007/service-review.json")), fixture());
        for (var source : documents) {
            var human = reviewNode(source);
            var result = (ObjectNode) human.path("resultContract");
            var schema = (ObjectNode) json.readTree("""
                    {"type":"object","properties":{"approvedAmount":{"type":"number","minimum":0},
                     "note":{"type":"string","maxLength":500}},"required":["approvedAmount","note"],"additionalProperties":false}
                    """);
            result.set("schema", schema);
            assertThat(check(source).failed()).isTrue();
            result.put("mode", "OPTIONAL");
            var document = codec.ingest(source.toString());
            var diagnostics = new DefinitionDiagnostics();
            var model = loader.model(document, diagnostics);
            assertThat(diagnostics.report().diagnostics()).isEmpty();
            assertThat(model.humans().get("REVIEW").resultContract()).isEqualTo(
                    new TaskResultContract(TaskResultContract.Mode.OPTIONAL, DefinitionContentCanonicalizer.canonical(schema)));
            assertThat(loader.model(codec.ingest(document.canonical()), new DefinitionDiagnostics(), false))
                    .isEqualTo(model);
            result.put("mode", "REQUIRED");
            result.remove("schema");
            assertThat(check(source).failed()).isTrue();
            result.set("schema", schema);
            assertThat(check(source).failed()).isFalse();
        }
    }

    @Test void namedOutputInMinimalFlowDerivesOnlyANonNullJsonDescriptor() throws Exception {
        var source = minimalFixture();
        var human = reviewNode(source);
        human.put("outputVariable", "reviewResult");
        assertThat(check(source).failed()).isTrue();
        allowResult(human);

        var doc = codec.ingest(source.toString());
        var diagnostics = new DefinitionDiagnostics();
        var model = loader.model(doc, diagnostics);
        assertThat(diagnostics.failed()).isFalse();
        assertThat(MinimalDefinitionLoader.extended(doc.tree())).isFalse();
        assertThat(model.extended()).isTrue();
        assertThat(model.humans().get("REVIEW").outputVariable()).isEqualTo("reviewResult");
        assertThat(model.namedOutputs()).containsEntry("reviewResult", "REVIEW");
        assertThat(model.variables()).containsOnlyKeys("reviewResult")
                .containsEntry("reviewResult", new ActionField("reviewResult", VariableValueType.JSON, false, false));
        assertThat(model.initialize()).isEmpty();
        assertThat(doc.tree().path("variables")).isEmpty();

        for (String invalid : List.of("null", "1", "\"\"", "\"1bad\"", "\"invalid\\n\"")) {
            human.set("outputVariable", json.readTree(invalid));
            assertThat(check(source).failed()).as(invalid).isTrue();
        }
        human.remove("outputVariable");
        ((ObjectNode) source.path("nodes").get(0)).put("outputVariable", "reviewResult");
        assertThat(check(source).failed()).isTrue();
    }

    @Test void namedOutputsShareTheVariableNamespaceAndCannotBeInitializedAtStart() throws Exception {
        var source = minimalFixture();
        var human = reviewNode(source);
        allowResult(human);
        human.put("outputVariable", "reviewResult");
        var variables = (ArrayNode) source.path("variables");
        variables.addObject().put("key", "reviewResult").put("type", "JSON").put("nullable", false);
        assertThat(check(source).failed()).isTrue();
        variables.removeAll();

        for (int i = 0; i < 99; i++) {
            variables.addObject().put("key", "var" + i).put("type", "LONG").put("nullable", false);
        }
        assertThat(check(source).failed()).isFalse();
        variables.addObject().put("key", "var99").put("type", "LONG").put("nullable", false);
        assertThat(check(source).report().diagnostics()).anyMatch(d -> "WF_DEF_LIMIT_EXCEEDED".equals(d.code()));
        variables.removeAll();

        ((ObjectNode) source.path("startPolicy")).putObject("initialize").putObject("reviewResult")
                .put("source", "literal").putObject("value");
        assertThat(check(source).report().diagnostics()).anyMatch(d -> "WF_DEF_INVALID_BINDING".equals(d.code()));
    }

    @Test void namedOutputsCannotShareANameAcrossExclusiveBranches() throws Exception {
        var source = fixture();
        var transitions = (ArrayNode) source.path("transitions");
        for (int i = 3; i <= 4; i++) {
            var edge = (ObjectNode) transitions.get(i);
            String previousTarget = edge.path("to").asText();
            var branch = reviewNode(source).deepCopy();
            branch.put("key", "review" + i);
            allowResult(branch);
            branch.put("outputVariable", "reviewResult" + i);
            ((ArrayNode) source.path("nodes")).add(branch);
            edge.put("to", "review" + i);
            transitions.addObject().put("key", "endReview" + i).put("from", "review" + i)
                    .put("to", previousTarget).put("outcome", "COMPLETE");
        }
        ((ObjectNode) transitions.get(4)).put("condition",
                "#hasVariable('reviewResult3') && #vars.reviewResult3.approvedAmount > 0");
        assertThat(check(source).failed()).isFalse();
        ((ObjectNode) source.path("nodes").get(7)).put("outputVariable", "reviewResult3");
        assertThat(check(source).report().diagnostics()).anyMatch(d -> "WF_DEF_INVALID_VARIABLE".equals(d.code()));
    }

    @Test void serviceInputsSelectAWorkingVariableOrOriginalSubmissionWithTheSameJsonContract() throws Exception {
        for (var source : List.of((ObjectNode) json.readTree(getClass().getResourceAsStream("/f007/service-review.json")), fixture())) {
            var service = (ObjectNode) source.path("nodes").get(1);
            service.put("action", "sandbox.consumeReview");
            service.putObject("outputs");
            var binding = service.putObject("inputs").putObject("review").put("source", "submission").put("nodeKey", "REVIEW");
            var registry = mock(ServiceActionRegistry.class);
            when(registry.descriptor("sandbox.recordQuantity")).thenReturn(new ServiceActionDescriptor("sandbox.recordQuantity",
                    List.of(new ActionField("quantity", VariableValueType.LONG, true, false)),
                    List.of(new ActionField("recordedQuantity", VariableValueType.LONG, true, false))));
            when(registry.descriptor("sandbox.consumeReview")).thenReturn(new ServiceActionDescriptor("sandbox.consumeReview",
                    List.of(new ActionField("review", VariableValueType.JSON, false, false)), List.of()));

            var human = reviewNode(source);
            assertThat(checkBindings(source, registry).failed()).isTrue(); // FORBIDDEN
            allowResult(human);
            assertThat(checkBindings(source, registry).failed()).isFalse(); // No outputVariable
            for (String invalidSelector : List.of("missing", "BEGIN", "#vars.review")) {
                binding.put("nodeKey", invalidSelector);
                assertThat(checkBindings(source, registry).failed()).as(invalidSelector).isTrue();
            }
            binding.put("nodeKey", "REVIEW").put("key", "reviewResult");
            assertThat(checkBindings(source, registry).failed()).isTrue();
            binding.remove("key");
            binding.putObject("value");
            assertThat(checkBindings(source, registry).failed()).isTrue();
            binding.remove("value");

            ((ObjectNode) source.path("startPolicy").path("initialize")).set("quantity", binding.deepCopy());
            assertThat(check(source).failed()).isTrue();
            ((ObjectNode) source.path("startPolicy").path("initialize")).putObject("quantity")
                    .put("source", "startInput").put("key", "quantity");
            ((ObjectNode) service.path("outputs")).set("quantity", binding.deepCopy());
            assertThat(check(source).failed()).isTrue();
            ((ObjectNode) service.path("outputs")).removeAll();

            human.put("outputVariable", "reviewResult");
            binding.remove("nodeKey");
            binding.put("source", "variable").put("key", "reviewResult");
            assertThat(checkBindings(source, registry).failed()).isFalse();

            binding.remove("key");
            binding.put("source", "submission").put("nodeKey", "REVIEW");
            when(registry.descriptor("sandbox.consumeReview")).thenReturn(new ServiceActionDescriptor("sandbox.consumeReview",
                    List.of(new ActionField("review", VariableValueType.LONG, false, false)), List.of()));
            assertThat(checkBindings(source, registry).failed()).isTrue();
        }
    }

    private DefinitionDiagnostics checkBindings(ObjectNode source, ServiceActionRegistry registry) {
        var diagnostics = new DefinitionDiagnostics();
        var model = loader.model(codec.ingest(source.toString()), diagnostics);
        if (!diagnostics.failed()) ServiceBindingValidation.check(model, registry, diagnostics);
        return diagnostics;
    }

    private void allowResult(ObjectNode human) {
        human.putObject("resultContract").put("mode", "OPTIONAL").putObject("schema")
                .put("type", "object").putObject("properties");
        ((ObjectNode) human.path("resultContract").path("schema")).put("additionalProperties", false);
    }

    @Test void claimPolicyDefaultsAndValidatesAcrossAllDefinitionSchemasWithoutRewritingSource() throws Exception {
        var documents = List.of(minimalFixture(),
                (ObjectNode) json.readTree(getClass().getResourceAsStream("/f007/service-review.json")), fixture());
        for (var source : documents) {
            var original = codec.ingest(source.toString());
            var diagnostics = new DefinitionDiagnostics();
            var model = loader.model(original, diagnostics);
            assertThat(diagnostics.failed()).isFalse();
            assertThat(model.humans().get("REVIEW").claimPolicy()).isEqualTo(TaskClaimPolicy.OPTIONAL);

            var required = (ObjectNode) json.readTree(original.canonical());
            reviewNode(required).put("claimPolicy", "REQUIRED");
            diagnostics = new DefinitionDiagnostics();
            model = loader.model(codec.ingest(required.toString()), diagnostics);
            assertThat(diagnostics.failed()).isFalse();
            assertThat(model.humans().get("REVIEW").claimPolicy()).isEqualTo(TaskClaimPolicy.REQUIRED);

            var explicitOptional = (ObjectNode) json.readTree(original.canonical());
            reviewNode(explicitOptional).put("claimPolicy", "OPTIONAL");
            var explicit = codec.ingest(explicitOptional.toString());
            assertThat(explicit.canonical()).contains("\"claimPolicy\":\"OPTIONAL\"");
            assertThat(Arrays.equals(explicit.hash(), original.hash())).isFalse();
            assertThat(codec.ingest(original.canonical()).canonical()).isEqualTo(original.canonical());

            for (String invalid : List.of("null", "\"AUTO\"")) {
                var invalidPolicy = (ObjectNode) json.readTree(original.canonical());
                reviewNode(invalidPolicy).set("claimPolicy", json.readTree(invalid));
                assertThat(check(invalidPolicy).failed()).as(invalid).isTrue();
            }
            var misplaced = (ObjectNode) json.readTree(original.canonical());
            ((ObjectNode) misplaced.path("nodes").get(0)).put("claimPolicy", "REQUIRED");
            assertThat(check(misplaced).failed()).isTrue();
        }
    }

    private ObjectNode reviewNode(ObjectNode document) {
        for (var node : document.path("nodes")) {
            if ("REVIEW".equals(node.path("key").asText())) return (ObjectNode) node;
        }
        throw new AssertionError("Missing review fixture");
    }

    private ObjectNode minimalFixture() throws Exception {
        return (ObjectNode) json.readTree("""
                {"schemaVersion":1,"key":"sample.workflow","version":1,"name":"Sample",
                 "startPolicy":{"inputContract":[]},"variables":[],"nodes":[
                   {"key":"BEGIN","type":"START","name":"Start"},
                   {"key":"REVIEW","type":"HUMAN_TASK","name":"Review","taskType":"REVIEW",
                    "assignment":{"resolver":"reviewer","inputs":{}},"completion":{"policy":"SINGLE"},
                    "resultContract":{"mode":"FORBIDDEN"},
                    "outcomes":[{"key":"COMPLETE","label":"Complete","requiresComment":false}]},
                   {"key":"DONE","type":"END","name":"Done","result":"COMPLETED"}],
                 "transitions":[{"key":"start","from":"BEGIN","to":"REVIEW"},
                                {"key":"finish","from":"REVIEW","to":"DONE","outcome":"COMPLETE"}]}
                """);
    }
}
