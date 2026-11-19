package io.github.leontecristian.workflow.web.dto.runtime;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Named controls derived from an already validated, pinned task result contract. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "formId", scope = WorkflowResultForm.class)
public record WorkflowResultForm(String kind,
        @JsonInclude(JsonInclude.Include.NON_NULL) @JsonSerialize(using = SharedText.class) String key,
        @JsonSerialize(using = SharedText.class) String label,
        @JsonSerialize(using = SharedText.class) String help,
        @JsonSerialize(using = SharedText.class) String hint,
        boolean required, boolean nullable, @JsonInclude(JsonInclude.Include.NON_NULL) Boolean inferred,
        String format, List<Choice> choices, @JsonSerialize(using = SharedText.class) String fixed,
        List<WorkflowResultForm> fields, WorkflowResultForm item,
        @JsonInclude(JsonInclude.Include.NON_NULL) @JsonSerialize(using = SharedText.class) String selectorKey,
        @JsonSerialize(using = SharedText.class) String selectorLabel,
        List<Alternative> alternatives, Map<String, String> limits, List<Dependency> dependencies, Rule rule) {

    public record Choice(@JsonSerialize(using = SharedText.class) String label,
            @JsonSerialize(using = SharedText.class) String value) {}
    public record Alternative(@JsonSerialize(using = SharedText.class) String label, WorkflowResultForm field) {}
    public record Dependency(Rule when, Rule then, @JsonInclude(JsonInclude.Include.NON_NULL) Rule otherwise) {}
    public record FieldRule(@JsonSerialize(using = SharedText.class) String key, Rule rule) {}

    /** Constraints for evaluating conditional controls in the browser. Values use exact JSON text. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "ruleId", scope = Rule.class)
    public record Rule(@JsonSerialize(using = SharedText.class) String label,
            @JsonSerialize(using = SharedText.class) String help,
            @JsonSerialize(using = SharedText.class) String hint,
            List<String> kinds, @JsonSerialize(contentUsing = SharedText.class) List<String> allowed,
            @JsonSerialize(using = SharedText.class) String matches,
            @JsonSerialize(contentUsing = SharedText.class) List<String> requiredFields, Boolean closed,
            List<FieldRule> fields, Rule item, Map<String, String> limits, String format,
            List<Rule> all, List<Rule> alternatives, List<Dependency> dependencies) {}

    /** Long business literals may be shared even when the fields using them have different keys. */
    static final class SharedText extends JsonSerializer<String> {
        private static final Object TEXT_IDS = new Object();
        private static final int INLINE_TEXT_LIMIT = 128;

        @Override public void serialize(String text, JsonGenerator generator, SerializerProvider provider) throws IOException {
            if (text.length() <= INLINE_TEXT_LIMIT) {
                generator.writeString(text);
                return;
            }
            @SuppressWarnings("unchecked")
            var ids = (Map<String, Integer>) provider.getAttribute(TEXT_IDS);
            if (ids == null) {
                ids = new HashMap<>();
                provider.setAttribute(TEXT_IDS, ids);
            }
            Integer existing = ids.get(text);
            int id = existing == null ? ids.size() + 1 : existing;
            generator.writeStartObject();
            generator.writeNumberField("textId", id);
            if (existing == null) {
                ids.put(text, id);
                generator.writeStringField("text", text);
            }
            generator.writeEndObject();
        }

        @Override public boolean isEmpty(SerializerProvider provider, String text) {
            return text.isEmpty();
        }
    }

    /** Uses the pinned object's property order and the exact numeric nodes supplied by the runtime codec. */
    public static WorkflowResultForm fromSchema(JsonNode schema) {
        if (schema == null || !schema.isObject()) throw new IllegalArgumentException("Result form is unavailable.");
        return new Projection(schema.path("$defs")).control(List.of(schema), null, "Result", true, true);
    }

    private static final class Projection {
        private static final Set<String> ALL_TYPES = Set.of("object", "array", "string", "number", "integer", "boolean", "null");
        private static final Set<String> ALTERNATIVE_PARENT_METADATA = Set.of("oneOf", "$ref", "$defs", "$schema", "title");
        private record ControlKey(List<Integer> fragments, String field, String fallback, boolean required, boolean root) {}

        private final JsonNode definitions;
        private final Map<JsonNode, Integer> fragmentIds = new IdentityHashMap<>();
        private final Map<ControlKey, WorkflowResultForm> controls = new HashMap<>();
        private final Map<List<Integer>, Rule> rules = new HashMap<>();

        private Projection(JsonNode definitions) {
            this.definitions = definitions;
        }

        private WorkflowResultForm control(List<JsonNode> parts, String key, String fallback, boolean required, boolean root) {
            return controlExpanded(expand(parts), key, fallback, required, root);
        }

        private WorkflowResultForm controlExpanded(List<JsonNode> fragments, String key, String fallback,
                boolean required, boolean root) {
            var cacheKey = new ControlKey(fragmentKey(fragments), key, fallback, required, root);
            var cached = controls.get(cacheKey);
            if (cached != null) return cached;

            String label = annotation(fragments, "title", fallback);
            String help = annotation(fragments, "description", null);
            JsonNode example = first(fragments, "examples", value -> !value.isEmpty() ? value.get(0) : null);
            String hint = example == null ? null : display(example);
            List<JsonNode> fixedValues = values(fragments, "const");
            List<JsonNode> allowed = choices(fragments, fixedValues);
            if (root && allowed != null) allowed.removeIf(value -> !value.isObject());
            Set<String> types = effectiveTypes(fragments);
            boolean nullable = !root && types.contains("null")
                    && (allowed == null || allowed.stream().anyMatch(JsonNode::isNull));
            String format = annotation(fragments, "format", null);
            Map<String, String> limits = limits(fragments);
            List<Dependency> dependencies = dependencies(fragments);

            String fixed = fixedValues.isEmpty() ? null : exact(fixedValues.get(0));
            List<Choice> choices = allowed == null ? null : allowed.stream()
                    .filter(value -> !value.isNull()).map(value -> new Choice(display(value), exact(value))).toList();
            String kind;
            if (fixed != null) kind = "fixed";
            else if (choices != null) kind = "choice";
            else kind = controlType(fragments, types, root ? "object" : null);
            Boolean inferred = !root && fixed == null && choices == null && values(fragments, "type").isEmpty() ? true : null;

            List<WorkflowResultForm> fields = null;
            WorkflowResultForm item = null;
            String selectorKey = null;
            String selectorLabel = null;
            List<Alternative> alternatives = null;
            if ("object".equals(kind)) {
                List<JsonNode> alternativeSets = values(fragments, "oneOf");
                if (alternativeSets.isEmpty()) {
                    var properties = properties(fragments);
                    var mandatory = required(fragments);
                    fields = properties.entrySet().stream().map(field -> control(field.getValue(), field.getKey(),
                            label(field.getKey()), mandatory.contains(field.getKey()), false)).toList();
                } else {
                    var source = firstSource(fragments, "oneOf");
                    var options = source.get("oneOf");
                    selectorKey = discriminator(options);
                    selectorLabel = annotation(List.of(options.get(0).path("properties").path(selectorKey)),
                            "title", label(selectorKey));
                    alternatives = new ArrayList<>();
                    for (JsonNode option : options) {
                        String value = option.path("properties").path(selectorKey).path("const").textValue();
                        String optionLabel = annotation(List.of(option), "title", label(value));
                        var conjunction = new ArrayList<>(fragments);
                        conjunction.remove(source);
                        conjunction.addAll(0, expand(List.of(option)));
                        // Keep the containing fragment's sibling validation and fields.
                        var containing = JsonNodeFactory.instance.objectNode();
                        for (var entry : source.properties()) {
                            if (!ALTERNATIVE_PARENT_METADATA.contains(entry.getKey()))
                                containing.set(entry.getKey(), entry.getValue());
                        }
                        if (!containing.isEmpty()) conjunction.add(containing);
                        if (canSelect(conjunction)) alternatives.add(new Alternative(optionLabel,
                                controlExpanded(conjunction, null, optionLabel, true, true)));
                    }
                }
            } else if ("array".equals(kind)) {
                item = control(values(fragments, "items"), null, "Item", true, false);
            }
            // The controls already describe the regular field tree. Only nontrivial sibling
            // intersections need an extra rule to preserve overlapping constraints.
            Rule rule = fragments.size() > 1 || fixed != null || choices != null ? ruleExpanded(fragments) : null;
            var field = new WorkflowResultForm(kind, key, label, help, hint, required, nullable, inferred, format, choices, fixed,
                    fields, item, selectorKey, selectorLabel, alternatives == null ? null : List.copyOf(alternatives),
                    limits, dependencies, rule);
            controls.put(cacheKey, field);
            return field;
        }

        private boolean canSelect(List<JsonNode> fragments) {
            var fields = properties(fragments);
            for (String name : required(fragments)) {
                var parts = fields.get(name);
                if (parts == null || conflicts(expand(parts))) return false;
            }
            return true;
        }

        private static boolean conflicts(List<JsonNode> fragments) {
            if (effectiveTypes(fragments).isEmpty()) return true;
            var fixed = values(fragments, "const");
            if (!fixed.isEmpty() && fixed.stream().anyMatch(value -> !sameValue(value, fixed.get(0)))) return true;
            var allowed = choices(fragments, fixed);
            return allowed != null && allowed.isEmpty();
        }

        private List<JsonNode> expand(List<JsonNode> parts) {
            var result = new ArrayList<JsonNode>();
            for (JsonNode part : parts) {
                for (JsonNode current = part; current != null; current = reference(current)) {
                    // A reference by itself contributes no presentation or validation rules.
                    if (current.size() != 1 || !current.has("$ref")) result.add(current);
                }
            }
            return result;
        }

        private List<Integer> fragmentKey(List<JsonNode> fragments) {
            // Source identity preserves the pinned property order, which JsonNode equality ignores.
            return fragments.stream().map(part -> fragmentIds.computeIfAbsent(part, unused -> fragmentIds.size())).toList();
        }

        private JsonNode reference(JsonNode fragment) {
            var ref = fragment.get("$ref");
            if (ref == null) return null;
            String pointer = URI.create(ref.textValue()).getFragment();
            String name = pointer.substring("/$defs/".length()).replace("~1", "/").replace("~0", "~");
            var target = definitions.get(name);
            if (target == null) throw new IllegalArgumentException("Result form is unavailable.");
            return target;
        }

        private static String controlType(List<JsonNode> fragments, Set<String> types, String fallback) {
            for (JsonNode fragment : fragments) {
                JsonNode type = fragment.path("type");
                if (type.isTextual()) return types.contains("integer") && "number".equals(type.textValue())
                        && !types.contains("number") ? "integer" : type.textValue();
                for (JsonNode choice : type)
                    if (!"null".equals(choice.textValue()) && types.contains(choice.textValue())) return choice.textValue();
            }
            if (fallback != null) return fallback;
            if (fragments.stream().anyMatch(part -> part.has("properties") || part.has("oneOf") || part.has("additionalProperties")))
                return "object";
            if (fragments.stream().anyMatch(part -> part.has("items"))) return "array";
            if (fragments.stream().anyMatch(part -> part.has("minimum") || part.has("maximum")
                    || part.has("exclusiveMinimum") || part.has("exclusiveMaximum"))) return "number";
            return "string";
        }

        private static Set<String> effectiveTypes(List<JsonNode> fragments) {
            Set<String> types = new LinkedHashSet<>(ALL_TYPES);
            for (JsonNode fragment : fragments) {
                List<String> declared = types(fragment.path("type"));
                if (declared.isEmpty()) continue;
                types.removeIf(type -> !declared.contains(type) && !("integer".equals(type) && declared.contains("number")));
            }
            return types;
        }

        private static List<String> types(JsonNode type) {
            if (type.isTextual()) return List.of(type.textValue());
            return strings(type);
        }

        private static List<JsonNode> choices(List<JsonNode> fragments, List<JsonNode> fixed) {
            List<JsonNode> selected = fixed.isEmpty() ? null : new ArrayList<>(List.of(fixed.get(0)));
            for (JsonNode restriction : values(fragments, "enum")) {
                if (selected == null) {
                    selected = new ArrayList<>();
                    restriction.forEach(selected::add);
                } else {
                    selected.removeIf(choice -> !containsValue(restriction, choice));
                }
            }
            if (selected != null) {
                Set<String> kinds = effectiveTypes(fragments);
                selected.removeIf(choice -> !kinds.contains(valueType(choice)));
            }
            return selected;
        }

        private static boolean containsValue(JsonNode choices, JsonNode answer) {
            for (JsonNode choice : choices) if (sameValue(choice, answer)) return true;
            return false;
        }

        private static boolean sameValue(JsonNode left, JsonNode right) {
            if (left.isNumber() && right.isNumber()) return left.decimalValue().compareTo(right.decimalValue()) == 0;
            if (left.isObject() && right.isObject() && left.size() == right.size())
                return left.properties().stream().allMatch(entry -> right.has(entry.getKey())
                        && sameValue(entry.getValue(), right.get(entry.getKey())));
            if (left.isArray() && right.isArray() && left.size() == right.size()) {
                for (int index = 0; index < left.size(); index++)
                    if (!sameValue(left.get(index), right.get(index))) return false;
                return true;
            }
            return left.equals(right);
        }

        private static String valueType(JsonNode value) {
            if (value.isNumber()) return value.decimalValue().stripTrailingZeros().scale() <= 0 ? "integer" : "number";
            return value.getNodeType().name().toLowerCase(Locale.ROOT);
        }

        private static Map<String, List<JsonNode>> properties(List<JsonNode> fragments) {
            Map<String, List<JsonNode>> properties = new LinkedHashMap<>();
            for (JsonNode fragment : fragments) {
                for (var field : fragment.path("properties").properties())
                    properties.computeIfAbsent(field.getKey(), ignored -> new ArrayList<>()).add(field.getValue());
            }
            for (JsonNode fragment : fragments)
                if (fragment.has("additionalProperties")) properties.keySet().removeIf(key -> !fragment.path("properties").has(key));
            return properties;
        }

        private static List<String> required(List<JsonNode> fragments) {
            return fragments.stream().flatMap(part -> strings(part.path("required")).stream()).distinct().toList();
        }

        private static String discriminator(JsonNode options) {
            for (JsonNode name : options.get(0).path("required")) {
                String key = name.textValue();
                Set<String> values = new LinkedHashSet<>();
                boolean valid = true;
                for (JsonNode option : options) {
                    var selector = option.path("properties").path(key);
                    valid = valid && "string".equals(selector.path("type").textValue())
                            && selector.path("const").isTextual() && strings(option.path("required")).contains(key)
                            && values.add(selector.path("const").textValue());
                }
                if (valid) return key;
            }
            throw new IllegalArgumentException("Result form is unavailable.");
        }

        private static Map<String, String> limits(List<JsonNode> fragments) {
            Map<String, String> limits = new LinkedHashMap<>();
            for (String limit : List.of("minLength", "maxLength", "minItems", "maxItems", "minProperties",
                    "maxProperties", "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum")) {
                boolean lower = limit.startsWith("min") || "exclusiveMinimum".equals(limit);
                JsonNode count = null;
                for (JsonNode candidate : values(fragments, limit)) {
                    if (count == null || (lower ? candidate.decimalValue().compareTo(count.decimalValue()) > 0
                            : candidate.decimalValue().compareTo(count.decimalValue()) < 0)) count = candidate;
                }
                if (count != null) limits.put(switch (limit) {
                    case "minProperties" -> "minFields";
                    case "maxProperties" -> "maxFields";
                    default -> limit;
                }, exact(count));
            }
            return Map.copyOf(limits);
        }

        private List<Dependency> dependencies(List<JsonNode> fragments) {
            var result = new ArrayList<Dependency>();
            for (JsonNode fragment : fragments) {
                if (fragment.has("if")) result.add(new Dependency(rule(fragment.get("if")),
                        rule(fragment.get("then")), fragment.has("else") ? rule(fragment.get("else")) : null));
            }
            return List.copyOf(result);
        }

        private Rule rule(JsonNode part) {
            return rule(List.of(part));
        }

        private Rule rule(List<JsonNode> parts) {
            return ruleExpanded(expand(parts));
        }

        private Rule ruleExpanded(List<JsonNode> fragments) {
            var key = fragmentKey(fragments);
            var cached = rules.get(key);
            if (cached != null) return cached;
            var rule = fragments.size() == 1 ? singleRule(fragments.get(0))
                    : new Rule(null, null, null, null, null, null, null, null, null, null, null, null,
                            fragments.stream().map(fragment -> ruleExpanded(List.of(fragment))).toList(), null, null);
            rules.put(key, rule);
            return rule;
        }

        private Rule singleRule(JsonNode part) {
            var fields = part.path("properties").properties().stream()
                    .map(entry -> new FieldRule(entry.getKey(), rule(entry.getValue()))).toList();
            var alternatives = new ArrayList<Rule>();
            for (JsonNode alternative : part.path("oneOf")) alternatives.add(rule(alternative));
            List<String> allowed = null;
            if (part.has("enum")) {
                allowed = new ArrayList<>();
                for (JsonNode value : part.path("enum")) allowed.add(exact(value));
            }
            JsonNode example = part.path("examples").get(0);
            return new Rule(annotation(List.of(part), "title", null), annotation(List.of(part), "description", null),
                    example == null ? null : display(example),
                    types(part.path("type")), allowed, part.has("const") ? exact(part.get("const")) : null,
                    strings(part.path("required")), part.has("additionalProperties") ? true : null,
                    fields, part.has("items") ? rule(part.get("items")) : null,
                    limits(List.of(part)), part.path("format").textValue(), null, List.copyOf(alternatives), dependencies(List.of(part)));
        }
    }

    private static JsonNode firstSource(List<JsonNode> fragments, String name) {
        return fragments.stream().filter(fragment -> fragment.has(name)).findFirst().orElseThrow();
    }

    private static List<JsonNode> values(List<JsonNode> fragments, String name) {
        return fragments.stream().filter(fragment -> fragment.has(name)).map(fragment -> fragment.get(name)).toList();
    }

    private static <T> T first(List<JsonNode> fragments, String name, Function<JsonNode, T> transform) {
        for (JsonNode fragment : fragments) {
            T value = transform.apply(fragment.path(name));
            if (value != null) return value;
        }
        return null;
    }

    private static List<String> strings(JsonNode values) {
        var strings = new ArrayList<String>();
        for (JsonNode value : values) strings.add(value.textValue());
        return strings;
    }

    private static String annotation(List<JsonNode> fragments, String name, String fallback) {
        String text = first(fragments, name, node -> node.isTextual() && !node.textValue().isBlank() ? node.textValue() : null);
        return text == null ? fallback : text;
    }

    private static String label(String key) {
        String words = key.replaceAll("([\\p{Ll}\\d])([\\p{Lu}])", "$1 $2")
                .replaceAll("([\\p{Lu}])([\\p{Lu}][\\p{Ll}])", "$1 $2")
                .replaceAll("[_-]+", " ").strip().toLowerCase(Locale.ROOT);
        if (words.isEmpty()) return "Value";
        int initial = words.offsetByCodePoints(0, 1);
        return words.substring(0, initial).toUpperCase(Locale.ROOT) + words.substring(initial);
    }

    private static String exact(JsonNode value) {
        return value.toString();
    }

    private static String display(JsonNode value) {
        if (value.isNull()) return "No value";
        if (value.isBoolean()) return value.booleanValue() ? "Yes" : "No";
        if (value.isTextual()) return value.textValue().isEmpty() ? "Empty text" : value.textValue();
        if (value.isObject()) {
            if (value.isEmpty()) return "Empty group";
            return String.join(", ", value.properties().stream()
                    .map(entry -> label(entry.getKey()) + ": " + display(entry.getValue())).toList());
        }
        if (value.isArray()) {
            if (value.isEmpty()) return "Empty list";
            var items = new ArrayList<String>();
            for (JsonNode item : value) items.add(display(item));
            return String.join(", ", items);
        }
        return exact(value);
    }
}
