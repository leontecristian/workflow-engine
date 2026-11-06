package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.DiagnosticStage;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Pure shared F004 structural/graph rules; no catalog, capability or persistence effects. */
@Component
public class MinimalDefinitionLoader {
    private final ObjectMapper json = new ObjectMapper();
    private final JsonSchema schema;
    private final String schemaDigest;
    private final JsonSchema extendedSchema;
    private final String extendedDigest;
    private final JsonSchema routingSchema;
    private final String routingDigest;
    private final io.github.leontecristian.workflow.core.service.expression.RestrictedExpressionService expressions;

    public MinimalDefinitionLoader() {
        this(new io.github.leontecristian.workflow.core.service.expression.RestrictedExpressionService());
    }
    @org.springframework.beans.factory.annotation.Autowired
    public MinimalDefinitionLoader(io.github.leontecristian.workflow.core.service.expression.RestrictedExpressionService expressions) {
        this.expressions=expressions;
        try (InputStream input = getClass().getResourceAsStream("/schemas/workflow-definition/minimal-v1.schema.json")) {
            if (input == null) throw new IllegalStateException("Missing bundled definition schema");
            byte[] bytes = input.readAllBytes();
            this.schemaDigest = DefinitionContentCanonicalizer.hex(DefinitionContentCanonicalizer.hash(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
            // Only the bundled schema and local fragment references are used. All external loads fail closed.
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
                    builder -> builder.schemaLoaders(loaders -> loaders.add(iri -> {
                        throw new IllegalStateException("External schema resolution is disabled");
                    })));
            this.schema = factory.getSchema(json.readTree(bytes), SchemaValidatorsConfig.builder()
                    .failFast(true).pathType(PathType.JSON_POINTER).typeLoose(false).build());
            schema.initializeValidators();
            try (InputStream extended = getClass().getResourceAsStream("/schemas/workflow-definition/scalar-service-v1.schema.json")) {
                if (extended == null) throw new IllegalStateException("Missing extended schema");
                byte[] content = extended.readAllBytes();
                extendedDigest = DefinitionContentCanonicalizer.hex(DefinitionContentCanonicalizer.hash(new String(content, java.nio.charset.StandardCharsets.UTF_8)));
                extendedSchema = factory.getSchema(json.readTree(content), SchemaValidatorsConfig.builder().failFast(true)
                        .pathType(PathType.JSON_POINTER).typeLoose(false).build());
                extendedSchema.initializeValidators();
            }
            try (InputStream routing = getClass().getResourceAsStream("/schemas/workflow-definition/exclusive-routing-v1.schema.json")) {
                if(routing==null) throw new IllegalStateException("Missing routing schema");
                byte[] content=routing.readAllBytes();
                routingDigest=DefinitionContentCanonicalizer.hex(DefinitionContentCanonicalizer.hash(new String(content,java.nio.charset.StandardCharsets.UTF_8)));
                routingSchema=factory.getSchema(json.readTree(content),SchemaValidatorsConfig.builder().failFast(true).pathType(PathType.JSON_POINTER).typeLoose(false).build());
                routingSchema.initializeValidators();
            }
        } catch (IOException failure) { throw new IllegalStateException("Cannot load bundled definition schema", failure); }
    }

    String schemaDigest() { return schemaDigest; }
    String schemaDigest(boolean extended) { return extended ? extendedDigest : schemaDigest; }
    String schemaDigest(JsonNode root) { return routing(root) ? routingDigest : schemaDigest(extended(root)); }
    static boolean routing(JsonNode root) {
        return java.util.stream.StreamSupport.stream(root.path("nodes").spliterator(),false).anyMatch(n->"EXCLUSIVE_GATEWAY".equals(n.path("type").asText()));
    }
    static boolean extended(JsonNode root) {
        return routing(root) || root.path("variables").size()>0 || root.path("startPolicy").path("inputContract").size()>0
                || root.path("startPolicy").path("initialize").size()>0
                || java.util.stream.StreamSupport.stream(root.path("nodes").spliterator(),false).anyMatch(n -> "SERVICE_TASK".equals(n.path("type").asText()));
    }

    MinimalWorkflowDefinition model(DefinitionDocumentCodec.Document document, DefinitionDiagnostics diagnostics) {
        boolean extended = extended(document.tree());
        unsupported(document.tree(), "", diagnostics, extended,routing(document.tree()));
        // Designer data is opaque. Avoid asking the structural validator to interpret arbitrary numeric values.
        ObjectNode validationTree = document.tree().deepCopy();
        if (validationTree.has("designer") && validationTree.get("designer").isObject()) validationTree.set("designer", json.createObjectNode());
        // These two fields were ingested as exact positive integral values; provide conventional integer nodes to the provider.
        validationTree.put("schemaVersion", 1); validationTree.put("version", document.version());
        Set<com.networknt.schema.ValidationMessage> errors;
        try {
            errors = (routing(document.tree()) ? routingSchema : extended ? extendedSchema : schema).validate(validationTree);
        } catch (RuntimeException failure) {
            // The default provider output converts fail-fast assertions into returned messages.
            // Other failures are infrastructure failures, never evidence that this document is invalid.
            throw new WorkflowDefinitionException(WorkflowDefinitionException.Category.HOST_CONFIGURATION, failure);
        }
        for (var error : errors) {
            String pointer=error.getInstanceLocation().toString();
            String code=error.getType().equals("additionalProperties") ? "UNKNOWN_FIELD" : "INVALID_STRUCTURE";
            if (extended) {
                if(pointer.startsWith("/variables")) code="INVALID_VARIABLE";
                else if(pointer.startsWith("/startPolicy/inputContract")) code="INVALID_INPUT_CONTRACT";
                else if(pointer.startsWith("/startPolicy/initialize") || pointer.contains("/inputs") || pointer.contains("/outputs")) code="INVALID_BINDING";
                else if(pointer.endsWith("/action")) code="ACTION_CONTRACT";
            }
            diagnostics.error(code,DiagnosticStage.STRUCTURE,"Document does not match the supported structure.",pointer);
        }
        return errors.isEmpty() ? (extended ? ExecutionValueDefinitionModel.load(document.tree(), diagnostics,routing(document.tree()) ? expressions : null) : graph(document.tree(), diagnostics)) : null;

    }

    private MinimalWorkflowDefinition graph(JsonNode root, DefinitionDiagnostics diagnostics) {
        Map<String, JsonNode> nodes = new HashMap<>();
        Map<String, List<JsonNode>> byType = new HashMap<>();
        for (JsonNode node : root.get("nodes")) {
            String key = node.get("key").textValue();
            if (nodes.putIfAbsent(key, node) != null) diagnostics.error("DUPLICATE_KEY", DiagnosticStage.GRAPH, "Node keys must be distinct.", "/nodes");
            byType.computeIfAbsent(node.get("type").textValue(), t -> new ArrayList<>()).add(node);
        }
        for (String type : List.of("START", "HUMAN_TASK", "END")) {
            if (byType.getOrDefault(type, List.of()).size() != 1) diagnostics.error("INVALID_GRAPH", DiagnosticStage.GRAPH,
                    "Exactly one START, HUMAN_TASK and END are required.", "/nodes");
        }
        if (diagnostics.failed()) return null;
        JsonNode start = byType.get("START").get(0), human = byType.get("HUMAN_TASK").get(0), end = byType.get("END").get(0);
        String startKey = start.get("key").textValue(), humanKey = human.get("key").textValue(), endKey = end.get("key").textValue();
        JsonNode outcome = human.get("outcomes").get(0);
        String outcomeKey = outcome.get("key").textValue();
        Set<String> transitionKeys = new HashSet<>(); int first = 0, last = 0;
        List<MinimalWorkflowDefinition.Transition> transitions = new ArrayList<>();
        for (JsonNode transition : root.get("transitions")) {
            String key = transition.get("key").textValue(), from = transition.get("from").textValue(), to = transition.get("to").textValue();
            String routeOutcome = DefinitionDocumentCodec.string(transition, "outcome");
            if (!transitionKeys.add(key)) diagnostics.error("DUPLICATE_KEY", DiagnosticStage.GRAPH, "Transition keys must be distinct.", "/transitions");
            if (!nodes.containsKey(from) || !nodes.containsKey(to)) diagnostics.error("UNKNOWN_NODE", DiagnosticStage.GRAPH, "Transition refers to an unknown node.", "/transitions");
            if (from.equals(startKey) && to.equals(humanKey) && routeOutcome == null) first++;
            else if (from.equals(humanKey) && to.equals(endKey) && outcomeKey.equals(routeOutcome)) last++;
            else diagnostics.error(from.equals(humanKey) && !outcomeKey.equals(routeOutcome) ? "UNKNOWN_OUTCOME" : "INVALID_GRAPH",
                    DiagnosticStage.GRAPH, "Transition is outside the required START to human to END path.", "/transitions");
            transitions.add(new MinimalWorkflowDefinition.Transition(key, from, to, routeOutcome));
        }
        if (first != 1 || last != 1) diagnostics.error("INVALID_GRAPH", DiagnosticStage.GRAPH, "Both required routes must occur exactly once.", "/transitions");
        String resolver = human.get("assignment").get("resolver").textValue();
        if (!DefinitionInputs.text(resolver, 100, 400, true)) diagnostics.error("INVALID_STRUCTURE", DiagnosticStage.CONTRACT, "Resolver must be a bounded logical identifier.", "/nodes");
        if (diagnostics.failed()) return null;
        return new MinimalWorkflowDefinition(new MinimalWorkflowDefinition.Node(startKey, "START", start.get("name").textValue(), null),
                new MinimalWorkflowDefinition.Human(humanKey, human.get("taskType").textValue(), new MinimalWorkflowDefinition.Assignment(resolver),
                        new MinimalWorkflowDefinition.Outcome(outcomeKey, outcome.get("label").textValue(), outcome.get("requiresComment").booleanValue())),
                new MinimalWorkflowDefinition.Node(endKey, "END", end.get("name").textValue(), end.get("result").textValue()), transitions);
    }

    private void unsupported(JsonNode node, String pointer, DefinitionDiagnostics diagnostics, boolean extended,boolean routing) {
        if (!node.isContainerNode()) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String field = entry.getKey(); JsonNode value = entry.getValue();
                if (field.equals("designer") || field.equals("default") || field.equals("value")) return;
                boolean unsupported = ((field.equals("guards") || (!extended && Set.of("variables", "inputContract").contains(field))) && value.isArray() && !value.isEmpty())
                        || (!extended && Set.of("inputs", "initialize").contains(field) && value.isObject() && !value.isEmpty())
                        || (field.equals("policy") && value.isTextual() && !value.textValue().equals("SINGLE"))
                        || (field.equals("mode") && value.isTextual() && !value.textValue().equals("FORBIDDEN"))
                        || (field.equals("type") && value.isTextual() && Set.of("DECISION", "PARALLEL_GATEWAY", "INCLUSIVE_GATEWAY").contains(value.textValue()))
                        || (field.equals("expressionPolicy") || (!routing && field.equals("condition")) || (!extended && field.equals("default")));
                String path = pointer + "/" + field.replace("~", "~0").replace("/", "~1");
                if (unsupported) diagnostics.error("UNSUPPORTED_FEATURE", DiagnosticStage.CONTRACT, "Recognized feature is not implemented by this publication subset.", path);
                unsupported(value, path, diagnostics, extended,routing);
            });
        } else for (int i = 0; i < node.size(); i++) unsupported(node.get(i), pointer + "/" + i, diagnostics, extended,routing);
    }
}
