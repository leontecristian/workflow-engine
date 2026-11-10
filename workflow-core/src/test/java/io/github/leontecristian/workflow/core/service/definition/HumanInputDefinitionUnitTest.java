package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HumanInputDefinitionUnitTest {
    private static final String SEQUENTIAL_REVIEW = """
            {
              "schemaVersion":1,"key":"review","version":1,"name":"Review",
              "startPolicy":{"inputContract":[]},"variables":[],
              "nodes":[
                {"key":"START","type":"START","name":"Start"},
                {"key":"MANAGER","type":"HUMAN_TASK","name":"Manager","taskType":"REVIEW",
                 "assignment":{"resolver":"reviewer","inputs":{}},"completion":{"policy":"SINGLE"},
                 "resultContract":{"mode":"FORBIDDEN"},"outcomes":[{"key":"DONE","label":"Done","requiresComment":false}],
                 "inputs":{"original":{"source":"submission","nodeKey":"FINANCE"}}},
                {"key":"FINANCE","type":"HUMAN_TASK","name":"Finance","taskType":"REVIEW",
                 "assignment":{"resolver":"reviewer","inputs":{}},"completion":{"policy":"SINGLE"},
                 "resultContract":{"mode":"OPTIONAL","schema":{"type":"object","properties":{},"additionalProperties":false}},
                 "outcomes":[{"key":"DONE","label":"Done","requiresComment":false}]},
                {"key":"END","type":"END","name":"End","result":"DONE"}],
              "transitions":[{"key":"begin","from":"START","to":"FINANCE"},
                {"key":"review","from":"FINANCE","to":"MANAGER","outcome":"DONE"},
                {"key":"finish","from":"MANAGER","to":"END","outcome":"DONE"}]
            }
            """;
    private final DefinitionDocumentCodec codec = new DefinitionDocumentCodec();
    private final MinimalDefinitionLoader loader = new MinimalDefinitionLoader();

    @Test
    void submissionBindingWorksInMinimalSchemaWithoutNamedOutputOrService() {
        var document = codec.ingest(SEQUENTIAL_REVIEW);
        assertThat(MinimalDefinitionLoader.extended(document.tree())).isFalse();
        var diagnostics = new DefinitionDiagnostics();
        var model = loader.model(document, diagnostics);
        assertThat(diagnostics.report().diagnostics()).isEmpty();
        var source = model.humans().get("MANAGER").inputs().get("original");
        assertThat(source.binding().nodeKey()).isEqualTo("FINANCE");
        assertThat(source.type()).isEqualTo(VariableValueType.JSON);
        assertThat(source.nullable()).isFalse();
        assertThat(model.humans().get("FINANCE").inputs()).isEmpty();
    }

    @Test
    void startInputRemainsValidAndHumanTypesComeFromVariablesIncludingNamedOutputs() {
        var root = codec.readObject(SEQUENTIAL_REVIEW);
        var startPolicy = (ObjectNode) root.get("startPolicy");
        startPolicy.withArray("inputContract").addObject().put("key", "quantity").put("type", "LONG")
                .put("required", true).put("nullable", true);
        startPolicy.putObject("initialize").putObject("quantity").put("source", "startInput").put("key", "quantity");
        root.withArray("variables").addObject().put("key", "quantity").put("type", "LONG").put("nullable", true);
        root.withArray("variables").addObject().put("key", "booking").put("type", "JSON").put("nullable", false);
        ((ObjectNode) root.path("nodes").get(2)).put("outputVariable", "workingResult");
        var bindings = (ObjectNode) root.path("nodes").get(1).get("inputs");
        bindings.putObject("expressionPolicy").put("source", "variable").put("key", "quantity");
        bindings.putObject("booking").put("source", "variable").put("key", "booking");
        bindings.putObject("working").put("source", "variable").put("key", "workingResult");
        var diagnostics = new DefinitionDiagnostics();
        var model = loader.model(codec.ingest(root.toString()), diagnostics);
        assertThat(diagnostics.report().diagnostics()).isEmpty();
        assertThat(model.inputs().get("quantity").field().required()).isTrue();
        var incoming = model.humans().get("MANAGER").inputs();
        assertThat(incoming.get("expressionPolicy").type()).isEqualTo(VariableValueType.LONG);
        assertThat(incoming.get("expressionPolicy").nullable()).isTrue();
        assertThat(incoming.get("booking").type()).isEqualTo(VariableValueType.JSON);
        assertThat(incoming.get("working").binding().source()).isEqualTo("variable");
        assertThat(incoming.get("working").type()).isEqualTo(VariableValueType.JSON);
        assertThat(incoming.get("working").nullable()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"bad name\":{\"source\":\"submission\",\"nodeKey\":\"FINANCE\"}",
            "\"data\":{\"source\":\"submission\",\"nodeKey\":\"bad key\"}",
            "\"data\":{\"source\":\"submission\",\"nodeKey\":\"UNKNOWN\"}",
            "\"data\":{\"source\":\"submission\",\"nodeKey\":\"END\"}",
            "\"data\":{\"source\":\"submission\",\"nodeKey\":\"MANAGER\"}",
            "\"data\":{\"source\":\"submission\",\"nodeKey\":\"FINANCE\",\"key\":\"FINANCE\"}",
            "\"data\":{\"source\":\"variable\",\"key\":\"UNKNOWN\"}",
            "\"data\":{\"source\":\"literal\",\"value\":{}}"
    })
    void rejectsInvalidNamesSelectorsAndSources(String entry) {
        var document = codec.ingest(SEQUENTIAL_REVIEW.replace(
                "\"original\":{\"source\":\"submission\",\"nodeKey\":\"FINANCE\"}", entry));
        var diagnostics = new DefinitionDiagnostics();
        assertThat(loader.model(document, diagnostics)).isNull();
        assertThat(diagnostics.report().diagnostics()).anySatisfy(diagnostic ->
                assertThat(diagnostic.pointer()).matches("/nodes/1(?:/inputs(?:/.*)?)?"));
    }

    @Test
    void limitsInputNamesAndRejectsDuplicates() {
        var root = codec.readObject(SEQUENTIAL_REVIEW);
        var inputs = ((ObjectNode) root.path("nodes").get(1)).putObject("inputs");
        for (int index = 0; index < 50; index++) {
            inputs.putObject("field" + index).put("source", "submission").put("nodeKey", "FINANCE");
        }
        var accepted = new DefinitionDiagnostics();
        assertThat(loader.model(codec.ingest(root.toString()), accepted).humans().get("MANAGER").inputs()).hasSize(50);
        inputs.putObject("extra").put("source", "submission").put("nodeKey", "FINANCE");
        var rejected = new DefinitionDiagnostics();
        assertThat(loader.model(codec.ingest(root.toString()), rejected)).isNull();
        assertThat(rejected.report().diagnostics()).anySatisfy(diagnostic ->
                assertThat(diagnostic.pointer()).isIn("/nodes/1", "/nodes/1/inputs"));
        assertThatThrownBy(() -> codec.ingest(SEQUENTIAL_REVIEW.replace("\"original\":",
                "\"original\":{},\"original\":")))
                .isInstanceOfSatisfying(WorkflowDefinitionException.class, failure ->
                        assertThat(failure.diagnostics()).anyMatch(d -> d.code().equals("WF_DEF_DUPLICATE_PROPERTY")));
    }

    @Test
    void authoringRejectsObsoleteFieldAndLegacyLoadingLeavesPublishedContentUntouched() {
        var root = codec.readObject(SEQUENTIAL_REVIEW);
        var human = (ObjectNode) root.path("nodes").get(1);
        human.remove("inputs");
        human.putArray("inputContract");
        var document = codec.ingest(root.toString());
        var authoring = new DefinitionDiagnostics();
        assertThat(loader.model(document, authoring)).isNull();
        assertThat(authoring.report().diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.pointer()).isEqualTo("/nodes/1/inputContract");
            assertThat(diagnostic.message()).contains("Remove", "inputContract");
        });
        var legacy = new DefinitionDiagnostics();
        assertThat(loader.model(document, legacy, false).humans().get("MANAGER").inputs()).isEmpty();
        assertThat(legacy.report().diagnostics()).isEmpty();
        assertThat(document.tree().path("nodes").get(1).has("inputContract")).isTrue();
        assertThat(document.hash()).isEqualTo(codec.ingest(document.tree().toString()).hash());

        human.withArray("inputContract").addObject().put("key", "oldScalar");
        var invalidLegacy = new DefinitionDiagnostics();
        assertThat(loader.model(codec.ingest(root.toString()), invalidLegacy, false)).isNull();
        assertThat(invalidLegacy.failed()).isTrue();
    }
}
