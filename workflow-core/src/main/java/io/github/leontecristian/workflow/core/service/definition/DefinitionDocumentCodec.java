package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class DefinitionDocumentCodec {
    private final JsonFactory factory = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
            .maxNestingDepth(64).maxNumberLength(128).maxStringLength(2 * 1024 * 1024).build()).build();

    record Document(ObjectNode tree, String canonical, byte[] hash, String key, int version, String name, String description) { }

    Document ingest(String source) {
        if (source == null || !DefinitionInputs.unicode(source)) throw DefinitionInputs.error("MALFORMED_JSON", "Invalid JSON text.");
        if (source.length() > 4 * 1024 * 1024 || source.getBytes(StandardCharsets.UTF_8).length > 4 * 1024 * 1024)
            throw DefinitionInputs.error("LIMIT_EXCEEDED", "Document exceeds its input budget.");
        JsonNode root;
        try (JsonParser parser = factory.createParser(source)) {
            if (parser.nextToken() == null) throw DefinitionInputs.error("MALFORMED_JSON", "An object document is required.");
            root = read(parser);
            if (parser.nextToken() != null || !root.isObject()) throw DefinitionInputs.error("MALFORMED_JSON", "Exactly one object document is required.");
        } catch (com.fasterxml.jackson.core.exc.StreamConstraintsException failure) {
            throw DefinitionInputs.error("LIMIT_EXCEEDED", "JSON parser limit exceeded.");
        } catch (IOException failure) {
            throw DefinitionInputs.error("MALFORMED_JSON", "Invalid JSON syntax.");
        } catch (ArithmeticException | NumberFormatException failure) {
            throw DefinitionInputs.error("MALFORMED_JSON", "Invalid exact numeric token.");
        }
        ObjectNode object = (ObjectNode) root;
        String key = string(object, "key"), name = string(object, "name");
        DefinitionInputs.require(DefinitionInputs.key(key));
        JsonNode schemaVersion = object.get("schemaVersion");
        if (schemaVersion == null || !schemaVersion.isNumber() || !schemaVersion.canConvertToInt()
                || schemaVersion.intValue() != 1) throw DefinitionInputs.error("UNSUPPORTED_SCHEMA_VERSION", "Only schema version 1 is supported.");
        JsonNode version = object.get("version");
        DefinitionInputs.require(version != null && version.isNumber() && version.canConvertToInt() && version.intValue() > 0);
        String description = string(object, "description");
        DefinitionInputs.require(!object.has("description") || description != null);
        DefinitionInputs.metadata(name, description);
        checkTexts(object, "", false);
        for (String field : new String[]{"nodes", "transitions"}) {
            JsonNode array = object.get(field);
            if (array != null && array.isArray() && array.size() > (field.equals("nodes") ? 200 : 500))
                throw DefinitionInputs.error("LIMIT_EXCEEDED", "Graph collection exceeds its limit.");
        }
        JsonNode designer = object.get("designer");
        if (designer != null) designerBounds(designer, 1);
        if (designer != null && DefinitionContentCanonicalizer.canonical(designer).length() * 2L > 256 * 1024)
            throw DefinitionInputs.error("LIMIT_EXCEEDED", "Designer metadata exceeds its storage budget.");
        String canonical = DefinitionContentCanonicalizer.canonical(object);
        if (canonical.length() * 2L > 2 * 1024 * 1024) throw DefinitionInputs.error("LIMIT_EXCEEDED", "Definition exceeds its storage budget.");
        return new Document(object, canonical, DefinitionContentCanonicalizer.hash(canonical), key, version.intValue(), name, description);
    }

    private JsonNode read(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.START_OBJECT) {
            ObjectNode object = JsonNodeFactory.instance.objectNode();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) throw new JsonParseException(parser, "Expected property");
                String name = parser.currentName();
                if (!DefinitionInputs.unicode(name)) throw DefinitionInputs.error("MALFORMED_JSON", "Invalid Unicode property.");
                if (object.has(name)) throw DefinitionInputs.error("DUPLICATE_PROPERTY", "Duplicate JSON property.");
                if (name.codePointCount(0, name.length()) > 256) throw DefinitionInputs.error("LIMIT_EXCEEDED", "Property name exceeds its limit.");
                parser.nextToken(); object.set(name, read(parser));
            }
            return object;
        }
        if (token == JsonToken.START_ARRAY) {
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            while (parser.nextToken() != JsonToken.END_ARRAY) array.add(read(parser));
            return array;
        }
        if (token == JsonToken.VALUE_STRING) {
            String value = parser.getText();
            if (!DefinitionInputs.unicode(value)) throw DefinitionInputs.error("MALFORMED_JSON", "Invalid Unicode string.");
            if (value.codePointCount(0, value.length()) > 16000) throw DefinitionInputs.error("LIMIT_EXCEEDED", "Metadata string exceeds its limit.");
            return JsonNodeFactory.instance.textNode(value);
        }
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT)
            return new DefinitionContentCanonicalizer.ExactNumber(parser.getText());
        if (token == JsonToken.VALUE_TRUE || token == JsonToken.VALUE_FALSE) return JsonNodeFactory.instance.booleanNode(token == JsonToken.VALUE_TRUE);
        if (token == JsonToken.VALUE_NULL) return JsonNodeFactory.instance.nullNode();
        throw new JsonParseException(parser, "Invalid value");
    }

    private void checkTexts(JsonNode node, String field, boolean designer) {
        if (node.isTextual() && !designer) {
            String value = node.textValue();
            if ((field.equals("name") || field.equals("label")) && !DefinitionInputs.text(value, 200, 400, true))
                throw DefinitionInputs.error("LIMIT_EXCEEDED", "Name or label exceeds its contract.");
            if (field.equals("description") && !DefinitionInputs.text(value, 2000, 4000, false))
                throw DefinitionInputs.error("LIMIT_EXCEEDED", "Description exceeds its contract.");
            if ((field.equals("key") || field.equals("taskType") || field.equals("from") || field.equals("to")
                    || field.equals("outcome") || field.equals("result")) && !DefinitionInputs.key(value))
                throw DefinitionInputs.error("INVALID_IDENTITY", "Invalid technical key.");
        }
        if (node.isObject()) node.fields().forEachRemaining(e -> { if (!e.getKey().equals("default") && !e.getKey().equals("value")) checkTexts(e.getValue(), e.getKey(), designer || e.getKey().equals("designer")); });
        else if (node.isArray()) node.forEach(n -> checkTexts(n, field, designer));
    }

    private void designerBounds(JsonNode node, int depth) {
        if (!node.isContainerNode()) return;
        if (depth > 16 || node.size() > (node.isObject() ? 100 : 1000))
            throw DefinitionInputs.error("LIMIT_EXCEEDED", "Designer structure exceeds its limit.");
        node.forEach(n -> designerBounds(n, depth + 1));
    }

    static String string(JsonNode node, String field) {
        JsonNode value = node.get(field); return value != null && value.isTextual() ? value.textValue() : null;
    }
}
