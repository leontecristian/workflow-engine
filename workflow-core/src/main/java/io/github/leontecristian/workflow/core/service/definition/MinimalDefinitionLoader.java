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
        return errors.isEmpty() ? ExecutionValueDefinitionModel.load(document.tree(), diagnostics, expressions) : null;

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
                        || (field.equals("expressionPolicy"));
                String path = pointer + "/" + field.replace("~", "~0").replace("/", "~1");
                if (unsupported) diagnostics.error("UNSUPPORTED_FEATURE", DiagnosticStage.CONTRACT, "Recognized feature is not implemented by this publication subset.", path);
                unsupported(value, path, diagnostics, extended,routing);
            });
        } else for (int i = 0; i < node.size(); i++) unsupported(node.get(i), pointer + "/" + i, diagnostics, extended,routing);
    }
}
