package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionCapabilities;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionValidationReport;
import io.github.leontecristian.workflow.core.dto.definition.DiagnosticStage;
import io.github.leontecristian.workflow.core.dto.definition.SingleAssignmentCapability;
import io.github.leontecristian.workflow.core.dto.definition.TaskTypeCapability;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeView;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Internal publication-subset checker. No represented host function is ever executed. */
@Component
public class DefinitionValidationService {
    static final String IDENTIFIER = "wf-minimal-single-validator-v1";
    private final TaskTypeCatalogService catalog;
    private final DefinitionCapabilityProvider provider;
    private final ObjectMapper json = new ObjectMapper();
    private final JsonSchema schema;
    private final String schemaDigest;

    public DefinitionValidationService(TaskTypeCatalogService catalog, ObjectProvider<DefinitionCapabilityProvider> providers) {
        this.catalog = catalog;
        List<DefinitionCapabilityProvider> registered = providers.stream().toList();
        if (registered.size() > 1) throw new WorkflowDefinitionException(WorkflowDefinitionException.Category.HOST_CONFIGURATION, List.of());
        this.provider = registered.isEmpty() ? null : registered.get(0);
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
        } catch (IOException failure) { throw new IllegalStateException("Cannot load bundled definition schema", failure); }
    }

    record Checked(DefinitionValidationReport report, byte[] contextHash, String detailsJson) { }

    Checked check(DefinitionDocumentCodec.Document document, boolean publication) {
        DefinitionDiagnostics diagnostics = new DefinitionDiagnostics();
        unsupported(document.tree(), "", diagnostics);
        // Designer data is opaque. Avoid asking the structural validator to interpret arbitrary numeric values.
        ObjectNode validationTree = document.tree().deepCopy();
        if (validationTree.has("designer") && validationTree.get("designer").isObject()) validationTree.set("designer", json.createObjectNode());
        // These two fields were ingested as exact positive integral values; provide conventional integer nodes to the provider.
        validationTree.put("schemaVersion", 1); validationTree.put("version", document.version());
        Set<com.networknt.schema.ValidationMessage> errors;
        try {
            errors = schema.validate(validationTree);
        } catch (RuntimeException failure) {
            // The default provider output converts fail-fast assertions into returned messages.
            // Other failures are infrastructure failures, never evidence that this document is invalid.
            throw new WorkflowDefinitionException(WorkflowDefinitionException.Category.HOST_CONFIGURATION, failure);
        }
        for (var error : errors) {
            diagnostics.error(error.getType().equals("additionalProperties") ? "UNKNOWN_FIELD" : "INVALID_STRUCTURE",
                    DiagnosticStage.STRUCTURE, "Document does not match the supported structure.", error.getInstanceLocation().toString());
        }
        MinimalWorkflowDefinition model = errors.isEmpty() ? graph(document.tree(), diagnostics) : null;
        DefinitionCapabilities capabilities = snapshot();
        ObjectNode provenance = json.createObjectNode();
        provenance.put("validator", IDENTIFIER); provenance.put("structuralSchemaHash", schemaDigest);
        provenance.put("schemaVersion", 1); provenance.put("canonicalization", DefinitionContentCanonicalizer.IDENTIFIER);
        provenance.putNull("task"); provenance.putNull("assignmentCapability"); provenance.putNull("taskCapability");
        if (model != null) {
            var human = model.human();
            TaskTypeView task = (publication ? catalog.findByCodeForPublication(human.taskType()) : catalog.findByCode(human.taskType())).orElse(null);
            if (task == null || !task.selectable()) diagnostics.error("TASK_TYPE_UNAVAILABLE", DiagnosticStage.HOST,
                    "Referenced task type must exist and be selectable.", null);
            if (task != null) {
                ObjectNode descriptor = provenance.putObject("task"); descriptor.put("code", task.code());
                descriptor.put("selectable", task.selectable()); descriptor.put("lockVersion", task.lockVersion());
            }
            SingleAssignmentCapability assignment = capabilities == null ? null : capabilities.assignments().stream()
                    .filter(c -> c.resolver().equals(human.assignment().resolver())).findFirst().orElse(null);
            TaskTypeCapability presentation = capabilities == null ? null : capabilities.taskTypes().stream()
                    .filter(c -> c.taskType().equals(human.taskType())).findFirst().orElse(null);
            if (assignment != null) provenance.putObject("assignmentCapability").put("resolver", assignment.resolver());
            if (presentation != null) {
                ObjectNode descriptor = provenance.putObject("taskCapability"); descriptor.put("taskType", presentation.taskType());
                var outcomes = descriptor.putArray("supportedOutcomes"); presentation.supportedOutcomes().stream().sorted().forEach(outcomes::add);
            }
            if (assignment == null || presentation == null || !presentation.supportedOutcomes().contains(human.outcome().key()))
                diagnostics.error("HOST_CAPABILITY_UNAVAILABLE", DiagnosticStage.HOST, "Required deployed host capability is unavailable.", null);
        } else if (capabilities == null) {
            diagnostics.error("HOST_CAPABILITY_UNAVAILABLE", DiagnosticStage.HOST, "Deployed host capability provider is unavailable.", null);
        }
        String context = DefinitionContentCanonicalizer.canonical(provenance);
        byte[] contextHash = DefinitionContentCanonicalizer.hash(context);
        DefinitionValidationReport report = diagnostics.report();
        ObjectNode evidence = json.createObjectNode(); evidence.put("reportSchemaVersion", 1);
        evidence.set("provenance", provenance); evidence.put("contextHash", DefinitionContentCanonicalizer.hex(contextHash));
        evidence.set("report", json.valueToTree(report));
        String details = DefinitionContentCanonicalizer.canonical(evidence);
        if (details.length() * 2L > 256 * 1024) throw DefinitionInputs.error("LIMIT_EXCEEDED", "Required validation provenance exceeds its storage budget.");
        return new Checked(report, contextHash, details);
    }

    private DefinitionCapabilities snapshot() {
        if (provider == null) return null;
        try {
            DefinitionCapabilities snapshot = provider.snapshot();
            if (snapshot == null) throw new IllegalArgumentException("Missing deployment snapshot");
            Set<String> ids = new HashSet<>();
            for (SingleAssignmentCapability c : snapshot.assignments()) {
                if (c == null || !DefinitionInputs.text(c.resolver(), 100, 400, true) || !ids.add(c.resolver()))
                    throw new IllegalArgumentException("Invalid resolver descriptor");
            }
            ids.clear();
            for (TaskTypeCapability c : snapshot.taskTypes()) {
                if (c == null || !DefinitionInputs.key(c.taskType()) || !ids.add(c.taskType())
                        || c.supportedOutcomes().stream().anyMatch(k -> !DefinitionInputs.key(k)))
                    throw new IllegalArgumentException("Invalid task descriptor");
            }
            return snapshot;
        } catch (RuntimeException failure) {
            throw new WorkflowDefinitionException(WorkflowDefinitionException.Category.HOST_CONFIGURATION, failure);
        }
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

    private void unsupported(JsonNode node, String pointer, DefinitionDiagnostics diagnostics) {
        if (!node.isContainerNode()) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String field = entry.getKey(); JsonNode value = entry.getValue();
                if (field.equals("designer")) return;
                boolean unsupported = (Set.of("variables", "inputContract", "guards").contains(field) && value.isArray() && !value.isEmpty())
                        || (Set.of("inputs", "initialize").contains(field) && value.isObject() && !value.isEmpty())
                        || (field.equals("policy") && value.isTextual() && !value.textValue().equals("SINGLE"))
                        || (field.equals("mode") && value.isTextual() && !value.textValue().equals("FORBIDDEN"))
                        || (field.equals("type") && value.isTextual() && Set.of("SERVICE_TASK", "EXCLUSIVE_GATEWAY", "DECISION", "PARALLEL_GATEWAY", "INCLUSIVE_GATEWAY").contains(value.textValue()))
                        || Set.of("condition", "default", "expressionPolicy").contains(field);
                String path = pointer + "/" + field.replace("~", "~0").replace("/", "~1");
                if (unsupported) diagnostics.error("UNSUPPORTED_FEATURE", DiagnosticStage.CONTRACT, "Recognized feature is not implemented by this publication subset.", path);
                unsupported(value, path, diagnostics);
            });
        } else for (int i = 0; i < node.size(); i++) unsupported(node.get(i), pointer + "/" + i, diagnostics);
    }
}
