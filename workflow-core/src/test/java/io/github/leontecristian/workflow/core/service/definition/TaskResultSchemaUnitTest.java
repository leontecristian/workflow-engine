package io.github.leontecristian.workflow.core.service.definition;

import com.networknt.schema.InvalidSchemaException;
import io.github.leontecristian.workflow.core.dto.definition.document.TaskResultContract;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskResultSchemaUnitTest {
    private final DefinitionDocumentCodec codec = new DefinitionDocumentCodec();

    @Test
    void mathematicalNumbersStaySeparateFromFingerprintScale() {
        var contract = checked("""
                {"type":"object","additionalProperties":false,"properties":{
                  "integer":{"type":"integer","minimum":1.00,"maximum":1e0},
                  "nested":{"const":{"amount":[1.00,0e-2147483647]}},
                  "choice":{"enum":[{"amount":[1.00]}]},
                  "edge":{"maximum":9999999999999999999999999999.1234567890}
                }}
                """);
        var value = WorkflowJsonValue.parse("""
                {"integer":1.0,"nested":{"amount":[1.000,0e+2147483647]},
                 "choice":{"amount":[1]},"edge":9999999999999999999999999999.1234567890}
                """);
        assertThat(TaskResultSchema.validate(contract, value)).isNull();
        assertThat(value.toJson()).contains("1.0", "1.000", "0E+2147483647", "9999999999999999999999999999.1234567890");
        assertThat(validate(contract, """
                {"edge":9999999999999999999999999999.1234567891}
                """)).isEqualTo(new TaskResultSchema.Violation("/edge", "/properties/edge/maximum"));
    }

    @Test
    void clampsCountKeywordsBeforeProviderIntegerConversion() {
        for (String keyword : new String[]{"Items", "Properties", "Length"}) {
            String value = switch (keyword) {
                case "Items" -> "[]";
                case "Properties" -> "{}";
                default -> "\"\"";
            };
            String closure = keyword.equals("Properties") ? ",\"additionalProperties\":false" : "";
            String template = """
                    {"type":"object","additionalProperties":false,"properties":{"value":{"%s":4294967296%s}}}
                    """;
            assertThat(validate(checked(template.formatted("max" + keyword, closure)), "{\"value\":" + value + "}")).isNull();
            assertThat(validate(checked(template.formatted("min" + keyword, closure)), "{\"value\":" + value + "}")).isNotNull();
        }
    }

    @Test
    void validatesExamplesAndReferenceSiblingsAgainstOriginalRoot() {
        var schema = checked("""
                {"type":"object","additionalProperties":false,"properties":{
                  "result":{"$ref":"#/$defs/a~1b%2B%20c","maxLength":6,"examples":["2026"]}
                },"$defs":{"a/b+ c":{"type":"string","minLength":4}}}
                """);
        assertThat(validate(schema, "{\"result\":\"2026\"}")).isNull();
        assertThat(validate(schema, "{\"result\":\"20\"}")).isNotNull();
        assertThat(validate(schema, "{\"result\":\"2026123\"}")).isNotNull();
        var badExample = report(schema.schemaJson().replace("\"2026\"", "\"20\""));
        assertThat(badExample.report().diagnostics()).anySatisfy(diagnostic ->
                assertThat(diagnostic.pointer()).endsWith("/properties/result/examples/0"));
    }

    @Test
    void localReferenceCanCloseTheRootWhileItsSiblingsConstrainIt() {
        var schema = checked("""
                {"type":"object","$ref":"#/$defs/010","maxProperties":1,
                 "$defs":{"010":{"properties":{"a":{"type":"integer"},"b":{"type":"integer"}},
                  "additionalProperties":false}}}
                """);
        assertThat(validate(schema, "{\"a\":1}")).isNull();
        assertThat(validate(schema, "{\"a\":1,\"b\":2}")).isNotNull();
        assertThat(validate(schema, "{\"unlisted\":1}")).isNotNull();
    }

    @Test
    void enforcesConditionalAlternativesAndFormats() {
        var schema = checked("""
                {"oneOf":[
                  {"type":"object","properties":{"kind":{"type":"string","const":"date"},"value":{"type":"string","format":"date"},"note":{"type":"string"}},
                   "required":["kind","value"],"additionalProperties":false,
                   "if":{"properties":{"value":{"const":"2026-01-01"}},"required":["value"]},"then":{"required":["note"]}},
                  {"type":"object","properties":{"kind":{"type":"string","const":"uuid"},"value":{"type":"string","format":"uuid"}},
                   "required":["kind","value"],"additionalProperties":false}
                ]}
                """);
        assertThat(validate(schema, "{\"kind\":\"date\",\"value\":\"2026-01-01\",\"note\":\"New Year\"}")).isNull();
        assertThat(validate(schema, "{\"kind\":\"date\",\"value\":\"2026-01-01\"}")).isNotNull();
        assertThat(validate(schema, "{\"kind\":\"date\",\"value\":\"2026-02-30\"}")).isNotNull();
        assertThat(validate(schema, "{\"kind\":\"uuid\",\"value\":\"550e8400-e29b-41d4-a716-446655440000\"}")).isNull();
        assertThat(validate(schema, "{\"kind\":\"uuid\",\"value\":\"550e\"}")).isNotNull();
    }

    @Test
    void referenceCanReuseAConditionalRequirementWithoutDefiningAnOpenObject() {
        String text = """
                {"type":"object","additionalProperties":false,
                 "properties":{"decision":{"type":"string"},"reason":{"type":"string"}},
                 "if":{"properties":{"decision":{"const":"REJECT"}},"required":["decision"]},
                 "then":{"$ref":"#/$defs/reject"},"$defs":{"reject":{"required":["reason"]}}}
                """;
        var schema = checked(text);
        assertThat(validate(schema, "{\"decision\":\"APPROVE\"}")).isNull();
        assertThat(validate(schema, "{\"decision\":\"REJECT\",\"reason\":\"Unavailable\"}")).isNull();
        assertThat(validate(schema, "{\"decision\":\"REJECT\"}")).isNotNull();
        assertThat(report(text.replace("\"reason\":{\"type\":\"string\"}",
                "\"reason\":{\"type\":\"string\"},\"nested\":{\"$ref\":\"#/$defs/reject\"}")).failed()).isTrue();
        assertThat(report(text.replace("\"required\":[\"reason\"]", "\"required\":[\"undeclared\"]")).failed()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}",
            "{\"type\":\"string\"}",
            "{\"type\":\"object\",\"const\":{\"unlisted\":1}}",
            "{\"enum\":[{\"unlisted\":1}]}",
            "{\"pattern\":\"x\"}",
            "{\"type\":\"object\"}",
            "{\"items\":false}",
            "{\"$ref\":\"classpath:schemas/workflow-definition/minimal-v1.schema.json\"}",
            "{\"$ref\":\"#/$defs/missing\"}",
            "{\"$defs\":{\"loop\":{\"$ref\":\"#/$defs/loop\"}}}",
            "{\"$defs\":{\"nested\":{\"$defs\":{}}}}",
            "{\"type\":[\"null\",\"string\",\"number\"]}",
            "{\"minimum\":1e10000}",
            "{\"maxLength\":1.1}",
            "{\"else\":{}}"
    })
    void rejectsOutsideTheProfile(String schema) {
        assertThat(report(schema).report().diagnostics()).singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.code()).isEqualTo("WF_DEF_INVALID_RESULT_SCHEMA"));
    }

    @Test
    void offlineRegistryRejectsUnapprovedClasspathAndRemoteResources() {
        var registry = DefinitionSchemas.offlineRegistry(false);
        for (String target : new String[]{"classpath:schemas/workflow-definition/minimal-v1.schema.json",
                "https://example.invalid/schema"}) {
            var schema = registry.getSchema(codec.readObject("{\"$ref\":\"" + target + "\"}"));
            assertThatThrownBy(schema::initializeValidators).isInstanceOf(InvalidSchemaException.class);
        }
    }

    @Test
    void rejectsExpansionAndValidationWorkBeforeProviderEvaluation() {
        var defs = new StringBuilder();
        defs.append("\"d0\":{\"type\":\"string\"}");
        for (int i = 1; i <= 15; i++) {
            defs.append(",\"d%d\":{\"properties\":{\"a\":{\"$ref\":\"#/$defs/d%d\"},\"b\":{\"$ref\":\"#/$defs/d%d\"}},\"additionalProperties\":false}"
                    .formatted(i, i - 1, i - 1));
        }
        assertThat(report("{\"$ref\":\"#/$defs/d15\",\"$defs\":{" + defs + "}}").report().diagnostics()).singleElement()
                .satisfies(diagnostic -> assertThat(diagnostic.code()).isEqualTo("WF_DEF_LIMIT_EXCEEDED"));
        var schema = checked("""
                {"properties":{"values":{"items":{"type":"integer","minimum":0,"maximum":99,
                 "enum":[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,
                  35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,
                  70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99]}}},
                 "additionalProperties":false}
                """);
        assertThatThrownBy(() -> validate(schema, "{\"values\":[" + "1,".repeat(999) + "1]}"))
                .isInstanceOf(TaskResultSchema.LimitExceeded.class)
                .hasMessageContaining("work budget");
    }

    @Test
    void countsWorkAcrossExamplesFromDifferentSchemaOccurrences() {
        String values = "0,".repeat(239) + "0";
        var contract = codec.readObject("""
                {"mode":"REQUIRED","schema":{"type":"object","additionalProperties":false,
                  "properties":{"values":{"type":"array","items":{"type":"integer"}}},
                  "examples":[{"values":[%s]}]}}
                """.formatted(values));
        var budget = new TaskResultSchema.DefinitionBudget();
        var diagnostics = new DefinitionDiagnostics();
        for (int i = 0; i < 20 && !diagnostics.failed(); i++) {
            TaskResultSchema.readContract(contract, "/resultContract", diagnostics, budget, true);
        }
        assertThat(diagnostics.report().diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("WF_DEF_LIMIT_EXCEEDED");
            assertThat(diagnostic.message()).contains("combined work budget");
        });
    }

    private TaskResultContract checked(String schema) {
        var diagnostics = new DefinitionDiagnostics();
        var result = read(schema, diagnostics);
        assertThat(diagnostics.report().diagnostics()).isEmpty();
        return result;
    }

    private DefinitionDiagnostics report(String schema) {
        var diagnostics = new DefinitionDiagnostics();
        read(schema, diagnostics);
        return diagnostics;
    }

    private TaskResultContract read(String schema, DefinitionDiagnostics diagnostics) {
        return TaskResultSchema.readContract(codec.readObject("{\"mode\":\"REQUIRED\",\"schema\":" + schema + "}"),
                "/resultContract", diagnostics, new TaskResultSchema.DefinitionBudget(), true);
    }

    private static TaskResultSchema.Violation validate(TaskResultContract schema, String value) {
        return TaskResultSchema.validate(schema, WorkflowJsonValue.parse(value));
    }
}
