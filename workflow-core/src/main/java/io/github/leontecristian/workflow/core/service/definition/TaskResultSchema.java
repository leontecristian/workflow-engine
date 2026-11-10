package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Schema;
import com.networknt.schema.path.NodePath;
import io.github.leontecristian.workflow.core.dto.definition.DiagnosticStage;
import io.github.leontecristian.workflow.core.dto.definition.document.TaskResultContract;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;

/** Engine-only structured result validation. Accepted JSON keeps its original canonical scale. */
public final class TaskResultSchema {
    private static final int VALUE_CHARS = 32_768;
    private static final int TOTAL_WORK = 1_000_000;
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final DefinitionDocumentCodec EXACT_JSON = new DefinitionDocumentCodec();
    private static final com.networknt.schema.SchemaRegistry REGISTRY = DefinitionSchemas.offlineRegistry(true);

    private TaskResultSchema() {}

    public record Violation(String path, String schemaPath) {}

    public static final class LimitExceeded extends RuntimeException {
        LimitExceeded(String message) { super(message); }
    }

    public static Violation validate(TaskResultContract contract, WorkflowJsonValue value) {
        if (value.toJson().length() > VALUE_CHARS) throw new LimitExceeded("Task result exceeds 64 KiB UTF-16.");
        if (!value.toJson().startsWith("{")) throw new IllegalArgumentException("Task result must be an object.");
        ObjectNode source = EXACT_JSON.readObject(contract.schemaJson());
        var profile = new TaskResultSchemaProfile(source);
        int weight = profile.weight(source);
        ObjectNode instance = EXACT_JSON.readObject(value.toJson());
        new WorkBudget().charge(weight, instance);
        var root = compile(profile, source);
        return mismatch(root, projectLiteral(instance, root.getSchemaLocation().getFragment()));
    }

    static final class DefinitionBudget {
        private static final int SCHEMA_CHARS = 32_768;
        private static final int TOTAL_SCHEMA_CHARS = 262_144;
        private final WorkBudget work = new WorkBudget();
        private int schemaChars;
    }

    static TaskResultContract readContract(JsonNode contract, String path, DefinitionDiagnostics diagnostics,
            DefinitionBudget budget, boolean authoring) {
        var mode = TaskResultContract.Mode.valueOf(contract.path("mode").textValue());
        if (mode == TaskResultContract.Mode.FORBIDDEN) return TaskResultContract.FORBIDDEN;
        var source = (ObjectNode) contract.get("schema");
        String schemaPath = path + "/schema";
        try {
            String text = DefinitionContentCanonicalizer.canonical(source);
            budget.schemaChars += text.length();
            if (text.length() > DefinitionBudget.SCHEMA_CHARS || budget.schemaChars > DefinitionBudget.TOTAL_SCHEMA_CHARS)
                throw new LimitExceeded("Embedded schemas exceed their UTF-16 size budget.");
            if (authoring) {
                var profile = new TaskResultSchemaProfile(source);
                validateExamples(profile, compile(profile, source), budget.work);
            }
            return new TaskResultContract(mode, text);
        } catch (LimitExceeded failure) {
            diagnostics.error("LIMIT_EXCEEDED", DiagnosticStage.CONTRACT, failure.getMessage(), schemaPath);
        } catch (TaskResultSchemaProfile.Invalid failure) {
            diagnostics.error("INVALID_RESULT_SCHEMA", DiagnosticStage.CONTRACT, failure.getMessage(), schemaPath + failure.pointer());
        } catch (com.networknt.schema.SchemaException failure) {
            diagnostics.error("INVALID_RESULT_SCHEMA", DiagnosticStage.CONTRACT, "The result schema cannot be compiled.", schemaPath);
        }
        return TaskResultContract.FORBIDDEN;
    }

    private static Schema compile(TaskResultSchemaProfile profile, ObjectNode source) {
        ObjectNode projected = (ObjectNode) projectLiteral(source, new NodePath(com.networknt.schema.path.PathType.JSON_POINTER));
        for (var fragment : profile.fragments()) {
            var location = fragment.location();
            var target = (ObjectNode) projected.at(location.toString());
            for (var entry : fragment.schema().properties()) {
                String key = entry.getKey();
                int cap = countCap(key);
                if (cap != 0) {
                    BigDecimal count = target.get(key).decimalValue();
                    target.put(key, count.min(BigDecimal.valueOf(cap + 1L)).intValueExact());
                }
            }
            if (fragment.reference() != null) {
                String name = TaskResultSchemaProfile.referenceName(fragment.schema().get("$ref"), location.append("$ref"));
                target.put("$ref", providerReference(name));
            }
        }
        var root = REGISTRY.getSchema(projected);
        root.initializeValidators();
        return root;
    }

    private static String providerReference(String name) {
        String pointerToken = name.replace("~", "~0").replace("/", "~1");
        try {
            String encoded = new URI(null, null, "/$defs/" + pointerToken, null).toASCIIString().replace("+", "%2B");
            // The provider interprets raw digit-only pointer segments as array indexes.
            if (!name.isEmpty() && name.chars().allMatch(c -> c >= '0' && c <= '9'))
                encoded = "/$defs/%3" + name.charAt(0) + name.substring(1);
            return "#" + encoded;
        } catch (URISyntaxException failure) {
            throw new IllegalStateException("Unable to encode checked schema reference", failure);
        }
    }

    private static int countCap(String key) {
        return switch (key) {
            case "minItems", "maxItems" -> 1000;
            case "minProperties", "maxProperties" -> 100;
            case "minLength", "maxLength" -> 16000;
            default -> 0;
        };
    }

    static JsonNode projectLiteral(JsonNode source, NodePath path) {
        if (source.isNumber()) return DecimalNode.valueOf(mathematicalNumber(source, path));
        if (source.isObject()) {
            var result = NODES.objectNode();
            for (var entry : source.properties())
                result.set(entry.getKey(), projectLiteral(entry.getValue(), path.append(entry.getKey())));
            return result;
        }
        if (source.isArray()) {
            var result = NODES.arrayNode();
            for (int i = 0; i < source.size(); i++) result.add(projectLiteral(source.get(i), path.append(i)));
            return result;
        }
        return source;
    }

    static BigDecimal mathematicalNumber(JsonNode node, NodePath path) {
        BigDecimal exact = node.decimalValue();
        BigDecimal normalized;
        try {
            normalized = exact.signum() == 0 ? BigDecimal.ZERO : exact.stripTrailingZeros();
        } catch (ArithmeticException failure) {
            throw TaskResultSchemaProfile.invalid(path, "A number must fit DECIMAL(38,10) exactly.");
        }
        if (Math.max(normalized.scale(), 0) > 10 || (long) normalized.precision() - normalized.scale() > 28)
            throw TaskResultSchemaProfile.invalid(path, "A number must fit DECIMAL(38,10) exactly.");
        return normalized;
    }

    private static void validateExamples(TaskResultSchemaProfile profile, Schema root, WorkBudget budget) {
        for (var fragment : profile.fragments()) {
            var examples = fragment.schema().path("examples");
            if (examples.isEmpty()) continue;
            var location = fragment.location();
            Schema containing = location.getNameCount() == 0 ? root : root.getSubSchema(location);
            int index = 0;
            for (var example : examples) {
                var pointer = location.append("examples").append(index++);
                if (DefinitionContentCanonicalizer.canonical(example).length() > VALUE_CHARS)
                    throw new LimitExceeded("A schema example exceeds its UTF-16 size budget.");
                checkExampleBounds(example, 1);
                budget.charge(profile.weight(fragment.schema()), example);
                if (mismatch(containing, projectLiteral(example, pointer)) != null)
                    throw TaskResultSchemaProfile.invalid(pointer, "The example does not satisfy its containing result schema.");
            }
        }
    }

    private static void checkExampleBounds(JsonNode value, int depth) {
        if (!value.isContainerNode()) return;
        if (depth > 16 || value.size() > (value.isObject() ? 100 : 1000))
            throw new LimitExceeded("A schema example exceeds the result depth or collection limit.");
        for (var child : value) checkExampleBounds(child, depth + 1);
    }

    private static Violation mismatch(Schema schema, JsonNode value) {
        var errors = schema.validate(value);
        if (errors.isEmpty()) return null;
        var error = errors.get(0);
        var instancePath = error.getInstanceLocation();
        if (error.getProperty() != null) instancePath = instancePath.append(error.getProperty());
        return new Violation(instancePath.toString(), error.getSchemaLocation().getFragment().toString());
    }

    private static final class WorkBudget {
        private int charged;

        void charge(int weight, JsonNode value) {
            long work = (long) weight * TaskResultSchemaProfile.nodeCount(value);
            if (work > TaskResultSchemaProfile.WORK_LIMIT)
                throw new LimitExceeded("Result validation exceeds its per-value work budget.");
            if (charged + work > TOTAL_WORK)
                throw new LimitExceeded("Result validation exceeds its combined work budget.");
            charged += (int) work;
        }
    }
}
