package io.github.leontecristian.workflow.testhost.service.console;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToLongFunction;

/** Private HTTP/evidence codec; never replaces the application's or core's ObjectMapper. */
@Component
public class ConsoleWire {
    private final ObjectMapper mapper;

    public ConsoleWire() {
        var longs =
                new SimpleModule()
                        .addSerializer(Long.class, ToStringSerializer.instance)
                        .addSerializer(Long.TYPE, ToStringSerializer.instance);
        mapper =
                new ObjectMapper(
                                JsonFactory.builder()
                                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                        .build())
                        .registerModule(longs)
                        .registerModule(new JavaTimeModule())
                        .registerModule(new Jdk8Module())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Console serialization failed", e);
        }
    }

    public JsonNode tree(String value) {
        try (var parser = mapper.createParser(value)) {
            JsonNode result = mapper.readTree(parser);
            if (result == null || parser.nextToken() != null) throw ConsoleException.invalid();
            return result;
        } catch (ConsoleException e) {
            throw e;
        } catch (Exception e) {
            throw ConsoleException.invalid();
        }
    }

    public ResponseEntity<String> response(Object value) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json(value));
    }

    public String raw(HttpServletRequest request, int limit) {
        if (request.getHeader("Content-Encoding") != null
                && !"identity".equalsIgnoreCase(request.getHeader("Content-Encoding")))
            throw new ConsoleException(415, "UNSUPPORTED_MEDIA_TYPE");
        String type = request.getContentType();
        if (type == null
                || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(type)))
            throw new ConsoleException(415, "UNSUPPORTED_MEDIA_TYPE");
        if (request.getContentLengthLong() > limit)
            throw new ConsoleException(413, "PAYLOAD_TOO_LARGE");
        try {
            byte[] bytes = request.getInputStream().readNBytes(limit + 1);
            if (bytes.length > limit) throw new ConsoleException(413, "PAYLOAD_TOO_LARGE");
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (ConsoleException e) {
            throw e;
        } catch (Exception e) {
            throw ConsoleException.invalid();
        }
    }

    public JsonNode body(HttpServletRequest request, String... fields) {
        JsonNode node = tree(raw(request, 16 * 1024));
        if (!node.isObject()) throw ConsoleException.invalid();
        Set<String> allowed = Set.of(fields);
        node.fieldNames()
                .forEachRemaining(
                        key -> {
                            if (!allowed.contains(key)) throw ConsoleException.invalid();
                        });
        return node;
    }

    public void empty(HttpServletRequest request) {
        try {
            if (request.getInputStream().read() != -1) throw ConsoleException.invalid();
        } catch (java.io.IOException e) {
            throw ConsoleException.invalid();
        }
    }

    public static String text(JsonNode node, String key, boolean nullable) {
        JsonNode value = node.get(key);
        if (nullable && (value == null || value.isNull())) return null;
        if (value == null || !value.isTextual()) throw ConsoleException.invalid();
        if (!ConsoleText.unicode(value.textValue())) throw ConsoleException.invalid();
        return value.textValue();
    }

    public static long decimal(String value, boolean zero) {
        if (value == null || !value.matches(zero ? "0|[1-9][0-9]*" : "[1-9][0-9]*"))
            throw ConsoleException.invalid();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw ConsoleException.invalid();
        }
    }

    public static long id(JsonNode node, String key, boolean zero) {
        return decimal(text(node, key, false), zero);
    }

    public static UUID uuid(JsonNode node) {
        String value = text(node, "commandId", false);
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equals(value)) throw ConsoleException.invalid();
            return id;
        } catch (IllegalArgumentException e) {
            throw ConsoleException.invalid();
        }
    }

    public static boolean bool(JsonNode node, String key) {
        if (!node.has(key) || !node.get(key).isBoolean()) throw ConsoleException.invalid();
        return node.get(key).booleanValue();
    }

    public static int limit(String value) {
        long result = value == null ? 50 : decimal(value, false);
        if (result > 100) throw ConsoleException.invalid();
        return (int) result;
    }

    public static long after(String value) {
        return value == null ? 0 : decimal(value, true);
    }

    public static long token(HttpServletRequest request) {
        return decimal(request.getHeader("X-Expected-Lock-Version"), true);
    }

    public static void query(HttpServletRequest request, String... keys) {
        Set<String> allowed = Set.of(keys);
        request.getParameterMap()
                .forEach(
                        (key, values) -> {
                            if (!allowed.contains(key) || values.length != 1)
                                throw ConsoleException.invalid();
                        });
    }

    public boolean fits(String value, int bound) {
        return value.length() * 2L <= bound
                && value.getBytes(StandardCharsets.UTF_8).length <= bound;
    }

    public <T> Map<String, Object> page(
            List<T> rows, Long sourceNext, String cursorName, ToLongFunction<T> cursor) {
        List<T> items = new ArrayList<>();
        Long next = sourceNext;
        for (int i = 0; i < rows.size(); i++) {
            T item = rows.get(i);
            items.add(item);
            Long candidateNext =
                    i + 1 < rows.size() ? Long.valueOf(cursor.applyAsLong(item)) : sourceNext;
            if (!fits(json(pageMap(items, candidateNext, cursorName)), 1024 * 1024)) {
                items.remove(items.size() - 1);
                if (items.isEmpty()) throw new ConsoleException(422, "LIMIT_EXCEEDED");
                next = cursor.applyAsLong(items.get(items.size() - 1));
                break;
            }
        }
        return pageMap(items, next, cursorName);
    }

    private Map<String, Object> pageMap(List<?> items, Long next, String name) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put(name, next);
        return result;
    }
}
