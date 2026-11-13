package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.PreviousDecisionsPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreviousDecisionsDefinitionUnitTest {
    private final ObjectMapper json = new ObjectMapper();
    private final MinimalDefinitionLoader loader = new MinimalDefinitionLoader();
    private final DefinitionDocumentCodec codec = new DefinitionDocumentCodec();

    private ObjectNode fixture(String schema) throws Exception {
        Path path = Path.of("docs/features/F011-sequential-human-stages/sequential-review.json");
        if (!Files.exists(path)) path = Path.of("..").resolve(path);
        var root = (ObjectNode) json.readTree(Files.readString(path));
        if (schema.equals("scalar")) ((ArrayNode) root.path("variables")).addObject()
                .put("key", "value").put("type", "STRING").put("nullable", true);
        if (schema.equals("routing")) {
            ((ArrayNode) root.path("nodes")).addObject().put("key", "gate").put("type", "EXCLUSIVE_GATEWAY").put("name", "Gate");
            ((ObjectNode) root.path("transitions").get(0)).put("to", "gate");
            ((ArrayNode) root.path("transitions")).addObject().put("key", "route").put("from", "gate").put("to", "MANAGER").put("default", true);
        }
        return root;
    }
    private ObjectNode partner(ObjectNode root) { return (ObjectNode) root.path("nodes").get(2); }
    private MinimalWorkflowDefinition load(ObjectNode root, boolean authoring) {
        var diagnostics = new DefinitionDiagnostics();
        var model = loader.model(codec.ingest(root.toString()), diagnostics, authoring);
        assertThat(diagnostics.report().diagnostics()).isEmpty();
        return model;
    }
    private DefinitionDiagnostics invalid(ObjectNode root) {
        var diagnostics = new DefinitionDiagnostics();
        assertThat(loader.model(codec.ingest(root.toString()), diagnostics)).isNull();
        assertThat(diagnostics.failed()).isTrue();
        return diagnostics;
    }
    @ParameterizedTest @ValueSource(strings={"previous-decision-review", "previous-collective-decisions"})
    void maintainedSandboxExamplesLoadUnderTheStrictContract(String name) throws Exception {
        Path path = Path.of("docs/features/F022-previous-decision-comments/" + name + ".json");
        if (!Files.exists(path)) path = Path.of("..").resolve(path);
        var model = load((ObjectNode) json.readTree(Files.readString(path)), true);
        assertThat(model.previousDecisions()).isEqualTo(PreviousDecisionsPolicy.ALL_PREVIOUS);
    }
    @ParameterizedTest @ValueSource(strings={"minimal", "scalar", "routing"})
    void defaultsInheritanceOverridesAndLegacyLoadingAcrossAllSchemas(String schema) throws Exception {
        var root = fixture(schema);
        String original = root.toString();
        assertThat(load(root, false).humans().values()).allSatisfy(h ->
                assertThat(h.previousDecisions()).isEqualTo(PreviousDecisionsPolicy.NONE));
        assertThat(root.toString()).isEqualTo(original);
        root.putObject("previousDecisions").put("mode", "ALL_PREVIOUS");
        assertThat(load(root, true).humans().get("PARTNER").previousDecisions()).isEqualTo(PreviousDecisionsPolicy.ALL_PREVIOUS);
        partner(root).putObject("previousDecisions").put("mode", "INHERIT");
        assertThat(load(root, true).humans().get("PARTNER").previousDecisions()).isEqualTo(PreviousDecisionsPolicy.ALL_PREVIOUS);
        partner(root).putObject("previousDecisions").put("mode", "NONE");
        assertThat(load(root, true).humans().get("PARTNER").previousDecisions()).isEqualTo(PreviousDecisionsPolicy.NONE);
        root.putObject("previousDecisions").put("mode", "NONE");
        partner(root).putObject("previousDecisions").put("mode", "ALL_PREVIOUS");
        assertThat(load(root, true).humans().get("PARTNER").previousDecisions()).isEqualTo(PreviousDecisionsPolicy.ALL_PREVIOUS);
        partner(root).putObject("previousDecisions").put("mode", "SELECTED").putArray("nodeKeys").add("MANAGER");
        var model = load(root, true);
        assertThat(model.humans().get("PARTNER").previousDecisions().nodeKeys()).containsExactly("MANAGER");
        assertThat(model.humans().get("PARTNER").name()).isEqualTo(partner(root).path("name").textValue());
        assertThatThrownBy(() -> model.humans().get("PARTNER").previousDecisions().nodeKeys().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
    @ParameterizedTest @ValueSource(strings={"minimal", "scalar", "routing"})
    void strictPolicyStructureOnEverySchemaPath(String schema) throws Exception {
        for (String setting : new String[]{"null", "{}", "{\"mode\":null}", "{\"mode\":\"UNKNOWN\"}",
                "{\"mode\":\"SELECTED\"}", "{\"mode\":\"SELECTED\",\"nodeKeys\":[]}",
                "{\"mode\":\"SELECTED\",\"nodeKeys\":[\"MANAGER\",\"MANAGER\"]}",
                "{\"mode\":\"NONE\",\"nodeKeys\":[\"MANAGER\"]}", "{\"mode\":\"INHERIT\",\"extra\":true}",
                "{\"mode\":\"SELECTED\",\"nodeKeys\":[null]}", "{\"mode\":\"SELECTED\",\"nodeKeys\":[\"invalid key\"]}"}) {
            var root = fixture(schema);
            partner(root).set("previousDecisions", json.readTree(setting));
            assertThat(invalid(root).report().diagnostics()).anyMatch(d -> d.pointer().startsWith("/nodes/2"));
        }
        for (String setting : new String[]{"null", "{\"mode\":\"INHERIT\"}", "{\"mode\":\"SELECTED\",\"nodeKeys\":[\"MANAGER\"]}",
                "{\"mode\":\"NONE\",\"nodeKeys\":[]}", "{\"mode\":\"NONE\",\"extra\":true}"}) {
            var root = fixture(schema);
            root.set("previousDecisions", json.readTree(setting));
            assertThat(invalid(root).report().diagnostics()).anyMatch(d -> d.pointer().startsWith("/previousDecisions"));
        }
        for (int index : new int[]{0, 3}) {
            var root = fixture(schema);
            ((ObjectNode) root.path("nodes").get(index)).putObject("previousDecisions").put("mode", "NONE");
            invalid(root);
        }
    }
    @ParameterizedTest @ValueSource(strings={"PARTNER", "missing", "BEGIN", "manager", "MANAGER"})
    void selectedSourcesMustBeDistinctOtherHumanPredecessors(String source) throws Exception {
        var root = fixture("minimal");
        // MANAGER cannot select a later stage; the other invalid cases belong to PARTNER.
        var receiving = source.equals("MANAGER") ? (ObjectNode) root.path("nodes").get(1) : partner(root);
        receiving.putObject("previousDecisions").put("mode", "SELECTED").putArray("nodeKeys")
                .add(source.equals("MANAGER") ? "PARTNER" : source);
        assertThat(invalid(root).report().diagnostics()).anySatisfy(d -> {
            assertThat(d.code()).isEqualTo("WF_DEF_INVALID_GRAPH");
            assertThat(d.pointer()).endsWith("/previousDecisions/nodeKeys/0");
        });
    }
    @Test void conditionallyExecutedSourceNeedNotBeOnEveryPathAndNodeArrayOrderDoesNotMatter() throws Exception {
        var root = fixture("routing");
        var edges = (ArrayNode) root.path("transitions");
        ((ObjectNode) edges.get(edges.size() - 1)).remove("default");
        ((ObjectNode) edges.get(edges.size() - 1)).put("condition", "true");
        edges.addObject().put("key", "skip").put("from", "gate").put("to", "PARTNER").put("default", true);
        partner(root).putObject("previousDecisions").put("mode", "SELECTED").putArray("nodeKeys").add("MANAGER");
        var nodes = (ArrayNode) root.path("nodes");
        nodes.add(nodes.remove(1));
        assertThat(load(root, true).humans().get("PARTNER").previousDecisions().nodeKeys()).containsExactly("MANAGER");
    }
}
