package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.path.NodePath;
import com.networknt.schema.path.PathType;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Checks the authored vocabulary and computes a conservative bound on expanded validation work. */
final class TaskResultSchemaProfile {
    static final int WORK_LIMIT = 100_000;
    private static final int DEPTH_LIMIT = 128;
    private static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";
    private static final Set<String> TYPES = Set.of("string", "boolean", "integer", "number", "object", "array");
    private static final Set<String> FORMATS = Set.of("date", "date-time", "uuid");
    private static final Set<String> COUNT_KEYS = Set.of("minProperties", "maxProperties", "minLength",
            "maxLength", "minItems", "maxItems");
    private static final Set<String> NUMBER_KEYS = Set.of("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum");
    private static final Pattern LOCAL_REFERENCE = Pattern.compile("/\\$defs/(?:[^/~]|~[01])*");
    private final JsonNode definitions;
    private final Map<JsonNode, Fragment> fragments = new IdentityHashMap<>();
    private final List<Fragment> orderedFragments = new ArrayList<>();
    private final List<Overlay> overlays = new ArrayList<>();
    private final Map<JsonNode, Cost> costs = new IdentityHashMap<>();
    private final Set<JsonNode> visiting = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    TaskResultSchemaProfile(ObjectNode root) {
        definitions = root.path("$defs");
        check(root, new NodePath(PathType.JSON_POINTER), null, false);
        // Check every fragment, including unused definitions.
        for (Fragment fragment : orderedFragments) cost(fragment, 0);
        checkResultRoot(root);
        Set<JsonNode> overlayReferences = identitySet();
        for (Overlay overlay : overlays)
            checkOverlayScope(overlay.schema(), overlay.properties(), overlay.condition(), overlayReferences);
        checkObjectShapes(overlayReferences);
    }

    int weight(JsonNode schema) { return costs.get(schema).weight(); }

    Iterable<Fragment> fragments() { return orderedFragments; }

    record Fragment(JsonNode schema, NodePath location, JsonNode reference, boolean objectShape, boolean overlay) {}

    private record Overlay(JsonNode schema, JsonNode properties, boolean condition) {}
    private record Cost(int weight, int depth) {}

    static final class Invalid extends IllegalArgumentException {
        private final String pointer;

        Invalid(String message, String pointer) {
            super(message);
            this.pointer = pointer;
        }

        String pointer() { return pointer; }
    }

    private void check(JsonNode schema, NodePath location, JsonNode enclosingProperties, boolean condition) {
        require(schema.isObject(), location, "A schema must be an object.");
        var properties = schema.path("properties");
        var type = concreteType(schema.path("type"));
        names(schema.path("required"), location.append("required"));
        if (enclosingProperties != null) overlays.add(new Overlay(schema, enclosingProperties, condition));
        if (schema.has("then") || schema.has("else"))
            require(schema.has("if"), location, "Conditional branches require an if schema.");
        if (schema.has("if"))
            require(schema.has("then") && closed(schema) && properties.isObject(), location.append("if"),
                    "A condition requires a then branch and a surrounding closed object with declared properties.");
        boolean objectShape = "object".equals(type) || schema.has("properties") || schema.has("required")
                || schema.has("additionalProperties") || schema.has("minProperties") || schema.has("maxProperties");
        JsonNode reference = null;
        for (var entry : schema.properties()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            NodePath path = location.append(key);
            switch (key) {
                case "$schema" -> require(location.getNameCount() == 0 && DIALECT.equals(value.textValue()), path,
                        "Only the embedded root may declare Draft 2020-12.");
                case "$defs" -> {
                    require(location.getNameCount() == 0 && value.isObject(), path,
                            "Definitions must be an object at the embedded root.");
                    checkChildren(value, path);
                }
                case "$ref" -> {
                    String name = referenceName(value, path);
                    reference = definitions.get(name);
                    require(reference != null, path, "The referenced definition does not exist.");
                }
                case "type" -> require(type != null, path, "Type must select a concrete type with optional nullability.");
                case "properties" -> {
                    require(value.isObject(), path, "Properties must be an object.");
                    checkChildren(value, path);
                }
                case "required", "const" -> { }
                case "enum" -> require(value.isArray(), path, "Enum must be an array.");
                case "additionalProperties" -> require(value.isBoolean() && !value.booleanValue(), path,
                        "Additional properties must be false.");
                case "items" -> check(value, path, null, false);
                case "if", "then", "else" -> check(value, path, properties, key.equals("if"));
                case "oneOf" -> {
                    require(value.isArray() && !value.isEmpty(), path, "OneOf must be a nonempty array.");
                    if (value.size() > 16) throw new TaskResultSchema.LimitExceeded("A schema can contain at most 16 oneOf alternatives.");
                    checkAlternatives(value, path);
                }
                case "format" -> require(value.isTextual() && FORMATS.contains(value.textValue()), path,
                        "Format must be date, date-time or uuid.");
                case "title" -> require(value.isTextual() && DefinitionInputs.text(value.textValue(), 200, 400, false), path,
                        "Title must be a bounded string.");
                case "description" -> require(value.isTextual() && DefinitionInputs.text(value.textValue(), 2000, 4000, false), path,
                        "Description must be a bounded string.");
                case "examples" -> require(value.isArray(), path, "Examples must be an array.");
                default -> {
                    if (COUNT_KEYS.contains(key)) {
                        require(value.isNumber(), path, "The count or length must be a nonnegative integer.");
                        var number = TaskResultSchema.mathematicalNumber(value, path);
                        require(number.signum() >= 0 && number.scale() <= 0, path,
                                "The count or length must be a nonnegative integer.");
                    } else if (NUMBER_KEYS.contains(key)) {
                        require(value.isNumber(), path, "The bound must be a number.");
                    } else throw invalid(path, "Unsupported result schema keyword.");
                }
            }
        }
        var fragment = new Fragment(schema, location, reference, objectShape, enclosingProperties != null);
        fragments.put(schema, fragment);
        orderedFragments.add(fragment);
    }

    private void checkChildren(JsonNode nodes, NodePath path) {
        for (var field : nodes.properties())
            check(field.getValue(), path.append(field.getKey()), null, false);
    }

    private void checkObjectShapes(Set<JsonNode> overlayReferences) {
        Set<JsonNode> standaloneReferences = identitySet();
        for (Fragment fragment : orderedFragments) {
            if (fragment.overlay() || overlayReferences.contains(fragment.schema())) continue;
            JsonNode ref = fragment.reference();
            while (ref != null && standaloneReferences.add(ref)) {
                ref = fragments.get(ref).reference();
            }
        }
        for (Fragment fragment : orderedFragments) {
            boolean overlayOnly = overlayReferences.contains(fragment.schema())
                    && !standaloneReferences.contains(fragment.schema());
            if (fragment.objectShape() && !fragment.overlay() && !overlayOnly) checkClosedShape(fragment);
        }
    }

    private static Set<JsonNode> identitySet() {
        return java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private void checkClosedShape(Fragment fragment) {
        var schema = fragment.schema();
        var location = fragment.location();
        var required = new HashSet<>(names(schema.path("required"), location.append("required")));
        boolean closure = false;
        for (JsonNode part = schema; part != null; part = fragments.get(part).reference()) {
            closure |= closed(part) || part.has("oneOf");
            required.removeIf(part.path("properties")::has);
        }
        require(closure, location, "Complete object shapes must close additional properties.");
        require(required.isEmpty(), location.append("required"), "Required fields must be declared properties.");
    }

    private void checkResultRoot(JsonNode root) {
        boolean closedShape = false;
        for (JsonNode part = root; part != null; part = fragments.get(part).reference()) {
            NodePath path = fragments.get(part).location();
            require(!part.has("type") || "object".equals(concreteType(part.get("type"))), path.append("type"),
                    "A result schema must describe an object.");
            require(!part.has("const") || part.get("const").isObject(), path.append("const"),
                    "A root const must be an object.");
            boolean objectChoice = false;
            if (part.has("enum")) {
                for (JsonNode option : part.get("enum")) objectChoice |= option.isObject();
                require(objectChoice, path.append("enum"), "A root enum must allow an object.");
            }
            closedShape |= closed(part) || part.has("oneOf");
        }
        require(closedShape, fragments.get(root).location(), "A result schema must describe a closed object.");
    }

    private void checkOverlayScope(JsonNode schema, JsonNode properties, boolean condition,
            Set<JsonNode> overlayReferences) {
        var fragment = fragments.get(schema);
        var location = fragment.location();
        var required = names(schema.path("required"), location.append("required"));
        require(required.stream().allMatch(properties::has), location.append("required"),
                "Conditional requirements must refer to surrounding properties.");
        for (var field : schema.path("properties").properties()) {
            String name = field.getKey();
            var path = location.append("properties").append(name);
            require(properties.has(name), path, "A conditional field must be declared on the surrounding object.");
            require(!condition || required.contains(name), path, "A field tested by if must be required by if.");
        }
        if (fragment.reference() != null) {
            overlayReferences.add(fragment.reference());
            checkOverlayScope(fragment.reference(), properties, condition, overlayReferences);
        }
        for (JsonNode alternative : schema.path("oneOf"))
            checkOverlayScope(alternative, properties, condition, overlayReferences);
    }

    private void checkAlternatives(JsonNode alternatives, NodePath path) {
        Set<String> common = null;
        var usedValues = new java.util.HashMap<String, Set<String>>();
        for (int index = 0; index < alternatives.size(); index++) {
            var alternative = alternatives.get(index);
            var location = path.append(index);
            require("object".equals(alternative.path("type").textValue()) && closed(alternative), location,
                    "Each alternative must define a complete closed object.");
            var identifying = new HashSet<String>();
            for (String name : names(alternative.path("required"), location.append("required"))) {
                var field = alternative.path("properties").path(name);
                if ("string".equals(field.path("type").textValue()) && field.path("const").isTextual()
                        && usedValues.computeIfAbsent(name, ignored -> new HashSet<>()).add(field.get("const").textValue()))
                    identifying.add(name);
            }
            if (common == null) common = identifying;
            else common.retainAll(identifying);
            require(!common.isEmpty(), location, "Alternatives must share a required string field with distinct const values.");
            check(alternative, location, null, false);
        }
    }

    static String referenceName(JsonNode ref, NodePath path) {
        require(ref.isTextual() && ref.textValue().startsWith("#"), path, "Only local root definitions may be referenced.");
        try {
            String pointer = new URI(ref.textValue()).getFragment();
            require(pointer != null && LOCAL_REFERENCE.matcher(pointer).matches(), path,
                    "A reference must target one named root definition.");
            return pointer.substring("/$defs/".length()).replace("~1", "/").replace("~0", "~");
        } catch (URISyntaxException failure) {
            throw invalid(path, "A reference must be a valid local JSON Pointer.");
        }
    }

    private static String concreteType(JsonNode type) {
        if (type.isTextual() && TYPES.contains(type.textValue())) return type.textValue();
        if (type.isArray() && type.size() == 2) {
            JsonNode first = type.get(0);
            JsonNode second = type.get(1);
            if ("null".equals(first.textValue()) && second.isTextual() && TYPES.contains(second.textValue()))
                return second.textValue();
            if ("null".equals(second.textValue()) && first.isTextual() && TYPES.contains(first.textValue()))
                return first.textValue();
        }
        return null;
    }

    private static Set<String> names(JsonNode names, NodePath path) {
        if (names.isMissingNode()) return Set.of();
        require(names.isArray(), path, "Required must be an array of distinct strings.");
        Set<String> result = new HashSet<>();
        for (JsonNode name : names)
            require(name.isTextual() && result.add(name.textValue()), path, "Required must contain distinct strings.");
        return result;
    }

    private static boolean closed(JsonNode schema) {
        return schema.path("additionalProperties").isBoolean() && !schema.path("additionalProperties").booleanValue();
    }

    private Cost cost(Fragment fragment, int depth) {
        var schema = fragment.schema();
        if (depth > DEPTH_LIMIT) throw depthLimit();
        Cost cached = costs.get(schema);
        if (cached != null) return cached;
        if (!visiting.add(schema)) throw invalid(fragment.location(), "Schema references must be acyclic.");
        Cost expanded = count(schema, true, depth);
        if (fragment.reference() != null) {
            Cost reference = cost(fragments.get(fragment.reference()), depth + 1);
            expanded = new Cost(saturatingAdd(expanded.weight(), reference.weight()),
                    Math.max(expanded.depth(), 1 + reference.depth()));
        }
        if (expanded.weight() > WORK_LIMIT) throw new TaskResultSchema.LimitExceeded("Expanded schema weight exceeds its limit.");
        if (expanded.depth() > DEPTH_LIMIT) throw depthLimit();
        visiting.remove(schema);
        costs.put(schema, expanded);
        return expanded;
    }

    private Cost count(JsonNode node, boolean root, int depth) {
        if (!root && fragments.containsKey(node)) return cost(fragments.get(node), depth);
        int weight = 1;
        int height = 1;
        for (JsonNode child : node) {
            Cost cost = count(child, false, depth + 1);
            weight = saturatingAdd(weight, cost.weight() + (node.isObject() ? 1 : 0));
            height = Math.max(height, 1 + cost.depth());
        }
        return new Cost(weight, height);
    }

    static int nodeCount(JsonNode node) {
        int count = 1;
        for (JsonNode child : node)
            count = saturatingAdd(count, nodeCount(child) + (node.isObject() ? 1 : 0));
        return count;
    }

    private static int saturatingAdd(int left, int right) {
        return Math.min(WORK_LIMIT + 1, left + right);
    }

    private static TaskResultSchema.LimitExceeded depthLimit() {
        return new TaskResultSchema.LimitExceeded("Expanded schema depth exceeds its limit.");
    }

    static void require(boolean valid, NodePath path, String message) {
        if (!valid) throw invalid(path, message);
    }

    static Invalid invalid(NodePath path, String message) { return new Invalid(message, path.toString()); }
}
