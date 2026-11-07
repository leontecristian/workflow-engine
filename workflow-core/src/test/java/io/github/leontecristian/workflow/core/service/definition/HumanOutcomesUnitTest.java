package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HumanOutcomesUnitTest {
    private final ObjectMapper json = new ObjectMapper();
    private final MinimalDefinitionLoader loader = new MinimalDefinitionLoader();
    private final DefinitionDocumentCodec codec = new DefinitionDocumentCodec();

    private ObjectNode fixture(int count, String variant) throws Exception {
        var root = (ObjectNode) json.readTree("""
                {"schemaVersion":1,"key":"review","version":1,"name":"Review",
                 "startPolicy":{"inputContract":[]},"variables":[],
                 "nodes":[{"key":"START","type":"START","name":"Start"},
                 {"key":"REVIEW","type":"HUMAN_TASK","name":"Review","taskType":"REVIEW",
                  "assignment":{"resolver":"reviewer","inputs":{}},"completion":{"policy":"SINGLE"},
                  "inputContract":[],"resultContract":{"mode":"FORBIDDEN"},"outcomes":[]},
                 {"key":"END","type":"END","name":"End","result":"DONE"}],
                 "transitions":[{"key":"begin","from":"START","to":"REVIEW"}]}
                """);
        var outcomes = (ArrayNode) root.path("nodes").get(1).path("outcomes");
        var edges = (ArrayNode) root.get("transitions");
        for (int index = 0; index < count; index++) {
            outcomes.addObject().put("key", "O" + index).put("label", "Choice " + index).put("requiresComment", index % 2 == 1);
            edges.addObject().put("key", "route" + index).put("from", "REVIEW").put("to", "END").put("outcome", "O" + index);
        }
        if (variant.equals("values")) ((ArrayNode) root.get("variables")).addObject().put("key", "data").put("type", "JSON").put("nullable", true);
        if (variant.equals("gateway")) {
            ((ArrayNode) root.get("nodes")).addObject().put("key", "GATE").put("type", "EXCLUSIVE_GATEWAY").put("name", "Gate");
            for (int index = 1; index < edges.size(); index++) ((ObjectNode) edges.get(index)).put("to", "GATE");
            edges.addObject().put("key", "finish").put("from", "GATE").put("to", "END").put("default", true);
        }
        return root;
    }

    private DefinitionDiagnostics check(ObjectNode root) {
        var diagnostics = new DefinitionDiagnostics();
        loader.model(codec.ingest(root.toString()), diagnostics);
        return diagnostics;
    }

    @ParameterizedTest @ValueSource(strings = {"plain", "values", "gateway"})
    void validatesBoundariesAndSharedDestinationsAcrossAllSchemaPaths(String variant) throws Exception {
        for (int count : List.of(1, 16)) {
            var root = fixture(count, variant);
            var diagnostics = new DefinitionDiagnostics();
            var model = loader.model(codec.ingest(root.toString()), diagnostics);
            assertThat(diagnostics.report().diagnostics()).isEmpty();
            assertThat(model.humans().get("REVIEW").outcomes()).hasSize(count);
            assertThat(model.humans().get("REVIEW").outcome("O0")).isPresent();
            assertThat(model.humans().get("REVIEW").outcome("o0")).isEmpty();
        }
        for (int count : List.of(0, 17)) assertThat(check(fixture(count, variant)).failed()).isTrue();
    }

    @Test void multipleEndsAndPresentationOrderAreIndependentOfRoutes() throws Exception {
        var root = fixture(2, "plain");
        ((ArrayNode) root.get("nodes")).addObject().put("key", "OTHER").put("type", "END").put("name", "Other").put("result", "OTHER");
        ((ObjectNode) root.path("transitions").get(2)).put("to", "OTHER");
        var choices = (ArrayNode) root.path("nodes").get(1).get("outcomes");
        choices.add(choices.remove(0));
        var diagnostics = new DefinitionDiagnostics();
        var model = loader.model(codec.ingest(root.toString()), diagnostics);
        assertThat(diagnostics.failed()).isFalse();
        assertThat(model.humans().get("REVIEW").outcomes()).extracting(o -> o.key()).containsExactly("O1", "O0");
        assertThat(model.ends()).containsKeys("END", "OTHER");
    }

    @Test void capabilitiesMustSupportEveryPinnedOutcomeAndContextTracksTheDeployedContract() throws Exception {
        var catalog = mock(io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService.class);
        when(catalog.findByCode("REVIEW")).thenReturn(java.util.Optional.of(
                new io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeView(1, "REVIEW", "Review", null, true, 0)));
        var supported = new java.util.concurrent.atomic.AtomicReference<>(java.util.Set.of("O0"));
        DefinitionCapabilityProvider provider = () -> new io.github.leontecristian.workflow.core.dto.definition.DefinitionCapabilities(
                java.util.Set.of(new io.github.leontecristian.workflow.core.dto.definition.SingleAssignmentCapability("reviewer", java.util.Set.of(io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER))),
                java.util.Set.of(new io.github.leontecristian.workflow.core.dto.definition.TaskTypeCapability("REVIEW", supported.get())));
        org.springframework.beans.factory.ObjectProvider<DefinitionCapabilityProvider> providers = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(providers.stream()).thenAnswer(call -> java.util.stream.Stream.of(provider));
        var actions = mock(io.github.leontecristian.workflow.core.service.runtime.ServiceActionRegistry.class);
        var validation = new DefinitionValidationService(catalog, providers, loader, actions, mock(io.github.leontecristian.workflow.core.service.runtime.RuntimeHostSupport.class));
        var document = codec.ingest(fixture(2, "plain").toString());
        var unavailable = validation.check(document, false);
        assertThat(unavailable.report().diagnostics()).anyMatch(d -> d.code().equals("WF_DEF_HOST_CAPABILITY_UNAVAILABLE"));
        supported.set(java.util.Set.of("O0", "O1"));
        var ready = validation.check(document, false);
        assertThat(ready.report().diagnostics()).isEmpty();
        assertThat(ready.contextHash()).isNotEqualTo(unavailable.contextHash());
        assertThat(json.readTree(ready.detailsJson()).path("provenance").path("validator").asText()).isEqualTo("wf-shared-assignment-validator-v1");
        assertThat(json.readTree(ready.detailsJson()).path("provenance").path("structuralSchemaHash").asText()).isEqualTo(loader.schemaDigest(document.tree()));
        var model = loader.model(document, new DefinitionDiagnostics());
        var definition = new io.github.leontecristian.workflow.core.dto.definition.document.ExecutableDefinition(1, 1, "review", 1, "hash",
                io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus.PUBLISHED, model);
        var reader = new WorkflowDefinitionRuntimeReader(null, null, null, codec, loader, validation, actions);
        reader.requireCapabilities(definition, true);
        supported.set(java.util.Set.of("O0"));
        assertThatThrownBy(() -> reader.requireCapabilities(definition, false)).isInstanceOf(io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "duplicateRoute", "unknown", "absent", "condition", "default", "nonhuman", "duplicateKey", "bypass", "cycle", "extraHuman", "deadEnd", "unreachable", "badLabel", "badKey", "commentType", "guard", "policy", "result"})
    void rejectsInvalidDeclarationsAndGraphsWithLocations(String mutation) throws Exception {
        var root = fixture(2, "plain");
        var nodes = (ArrayNode) root.get("nodes");
        var human = (ObjectNode) nodes.get(1);
        var choices = (ArrayNode) human.get("outcomes");
        var edges = (ArrayNode) root.get("transitions");
        var edge = (ObjectNode) edges.get(2);
        switch (mutation) {
            case "missing" -> edges.remove(2);
            case "duplicateRoute" -> edge.put("outcome", "O0");
            case "unknown" -> edge.put("outcome", "UNKNOWN");
            case "absent" -> edge.remove("outcome");
            case "condition" -> edge.put("condition", "true");
            case "default" -> edge.put("default", true);
            case "nonhuman" -> ((ObjectNode) edges.get(0)).put("outcome", "O0");
            case "duplicateKey" -> ((ObjectNode) choices.get(1)).put("key", "O0");
            case "bypass" -> ((ObjectNode) edges.get(0)).put("to", "END");
            case "cycle" -> edge.put("to", "REVIEW");
            case "extraHuman" -> nodes.add(human.deepCopy().put("key", "OTHER"));
            case "deadEnd" -> { nodes.addObject().put("key", "SERVICE").put("type", "SERVICE_TASK").put("name", "Dead").put("action", "test").putObject("inputs"); ((ObjectNode) nodes.get(3)).putObject("outputs"); edge.put("to", "SERVICE"); }
            case "unreachable" -> nodes.addObject().put("key", "OTHER").put("type", "END").put("name", "Unused").put("result", "OTHER");
            case "badLabel" -> ((ObjectNode) choices.get(1)).put("label", "");
            case "badKey" -> ((ObjectNode) choices.get(1)).put("key", "bad key");
            case "commentType" -> ((ObjectNode) choices.get(1)).put("requiresComment", "true");
            case "guard" -> ((ObjectNode) choices.get(1)).putArray("guards").addObject();
            case "policy" -> ((ObjectNode) human.get("completion")).put("policy", "ALL");
            case "result" -> ((ObjectNode) human.get("resultContract")).put("mode", "REQUIRED");
            default -> throw new IllegalArgumentException();
        }
        if (List.of("badLabel", "badKey").contains(mutation)) {
            assertThatThrownBy(() -> check(root)).isInstanceOfSatisfying(
                    io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException.class,
                    failure -> assertThat(failure.diagnostics()).anyMatch(d -> d.pointer().startsWith("/nodes/1/outcomes/1/")));
            return;
        }
        var diagnostics = check(root);
        assertThat(diagnostics.failed()).as(mutation).isTrue();
        assertThat(diagnostics.report().diagnostics()).allSatisfy(d -> assertThat(d.pointer()).isNotBlank());
        if (mutation.equals("duplicateKey")) assertThat(diagnostics.report().diagnostics()).anyMatch(d -> d.code().equals("WF_DEF_DUPLICATE_KEY"));
        if (mutation.equals("missing")) assertThat(diagnostics.report().diagnostics()).anyMatch(d -> d.code().equals("WF_DEF_INVALID_GRAPH") && d.pointer().equals("/nodes/1/outcomes/1"));
        if (List.of("unknown", "condition", "default", "nonhuman").contains(mutation))
            assertThat(diagnostics.report().diagnostics()).anyMatch(d -> d.code().equals("WF_DEF_INVALID_GRAPH") && d.pointer().startsWith("/transitions/"));
    }
}
