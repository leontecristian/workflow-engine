package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.runtime.CompletionPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AllDefinitionUnitTest {
    private final DefinitionDocumentCodec codec = new DefinitionDocumentCodec();
    private final MinimalDefinitionLoader loader = new MinimalDefinitionLoader();
    private static final String MINIMAL = """
            {"schemaVersion":1,"key":"review","version":1,"name":"Review",
             "startPolicy":{"inputContract":[]},"variables":[],
             "nodes":[{"key":"START","type":"START","name":"Start"},
               {"key":"REVIEW","type":"HUMAN_TASK","name":"Review","taskType":"REVIEW",
                "assignment":{"resolver":"reviewers","inputs":{}},
                "completion":{"policy":"ALL","contributionOutcome":"DONE"},
                "resultContract":{"mode":"FORBIDDEN"},
                "outcomes":[{"key":"DONE","label":"Done","requiresComment":false}]},
               {"key":"END","type":"END","name":"End","result":"DONE"}],
             "transitions":[{"key":"begin","from":"START","to":"REVIEW"},
               {"key":"finish","from":"REVIEW","to":"END","outcome":"DONE"}]}
            """;

    @Test
    void pinsAllWithoutRequiringAnExtendedDefinitionBranch() {
        var document = codec.ingest(MINIMAL);
        assertThat(MinimalDefinitionLoader.extended(document.tree())).isFalse();
        var diagnostics = new DefinitionDiagnostics();
        var model = loader.model(document, diagnostics);
        assertThat(diagnostics.report().diagnostics()).isEmpty();
        assertThat(model.humans().get("REVIEW").completionPolicy()).isEqualTo(CompletionPolicy.ALL);
        assertThat(model.humans().get("REVIEW").contributionOutcome()).isEqualTo("DONE");
        assertThat(loader.model(document, new DefinitionDiagnostics(), false)).isEqualTo(model);
    }

    @Test
    void singleDocumentsAndExistingHumanConstructorsKeepSingleSemantics() {
        var diagnostics = new DefinitionDiagnostics();
        var model = loader.model(codec.ingest(MINIMAL.replace(
                "{\"policy\":\"ALL\",\"contributionOutcome\":\"DONE\"}", "{\"policy\":\"SINGLE\"}")), diagnostics);
        assertThat(diagnostics.report().diagnostics()).isEmpty();
        var source = model.humans().get("REVIEW");
        var copied = new io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Human(
                source.key(), source.taskType(), source.assignment(), source.outcomes(), source.claimPolicy(),
                source.resultContract(), source.outputVariable(), source.inputs());
        assertThat(copied).usingRecursiveComparison().ignoringFields("name").isEqualTo(source);
        assertThat(copied.name()).isEqualTo("REVIEW");
        assertThat(source.name()).isEqualTo("Review");
        assertThat(source.completionPolicy()).isEqualTo(CompletionPolicy.SINGLE);
        assertThat(source.contributionOutcome()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"policy\":\"ALL\"}",
            "{\"policy\":\"ALL\",\"contributionOutcome\":null}",
            "{\"policy\":\"ALL\",\"contributionOutcome\":\"UNKNOWN\"}",
            "{\"policy\":\"ALL\",\"contributionOutcome\":\"DONE\",\"extra\":true}",
            "{\"policy\":\"SINGLE\",\"contributionOutcome\":\"DONE\"}",
            "{\"policy\":\"ANY\",\"contributionOutcome\":\"DONE\"}"})
    void rejectsInvalidPolicyContracts(String completion) {
        var diagnostics = new DefinitionDiagnostics();
        assertThat(loader.model(codec.ingest(MINIMAL.replace(
                "{\"policy\":\"ALL\",\"contributionOutcome\":\"DONE\"}", completion)), diagnostics)).isNull();
        assertThat(diagnostics.failed()).isTrue();
    }

    @Test
    void extendedSchemasPreserveAllCommonInputsAndNamedOutputs() throws Exception {
        for (String resource : new String[]{"/f007/service-review.json", "/f008/quantity-routing.json"}) {
            var root = (ObjectNode) new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(getClass().getResourceAsStream(resource));
            for (var node : root.path("nodes")) {
                if (!"HUMAN_TASK".equals(node.path("type").asText())) continue;
                var human = (ObjectNode) node;
                human.putObject("completion").put("policy", "ALL")
                        .put("contributionOutcome", human.path("outcomes").get(0).path("key").asText());
                human.putObject("resultContract").put("mode", "OPTIONAL").putObject("schema")
                        .put("type", "object").putObject("properties");
                ((ObjectNode)human.path("resultContract").path("schema")).put("additionalProperties", false);
                human.put("outputVariable", "answers");
                human.putObject("inputs").putObject("original").put("source", "submission").put("nodeKey", human.path("key").asText());
            }
            var diagnostics = new DefinitionDiagnostics();
            var model = loader.model(codec.ingest(root.toString()), diagnostics);
            assertThat(diagnostics.report().diagnostics()).isEmpty();
            var human = model.humans().get("REVIEW");
            assertThat(human.completionPolicy()).isEqualTo(CompletionPolicy.ALL);
            assertThat(human.outputVariable()).isEqualTo("answers");
            assertThat(human.inputs().get("original").type()).isEqualTo(VariableValueType.JSON);
            assertThat(human.inputs().get("original").nullable()).isFalse();
        }
    }

    @Test
    void forbiddenResultsCannotProduceAnOutputOrSubmissionInput() {
        for (String field : new String[]{"\"outputVariable\":\"answers\",",
                "\"inputs\":{\"original\":{\"source\":\"submission\",\"nodeKey\":\"REVIEW\"}},"}) {
            var diagnostics = new DefinitionDiagnostics();
            assertThat(loader.model(codec.ingest(MINIMAL.replace("\"assignment\":", field + "\"assignment\":")), diagnostics)).isNull();
            assertThat(diagnostics.failed()).isTrue();
        }
    }
}
