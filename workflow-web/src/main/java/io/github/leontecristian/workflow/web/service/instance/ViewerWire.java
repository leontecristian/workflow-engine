package io.github.leontecristian.workflow.web.service.instance;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmission;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.web.exception.instance.WorkflowViewerException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;


import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

/** Private HTTP/evidence codec; never replaces the application's or core's ObjectMapper. */

public class ViewerWire {
    private final ObjectMapper mapper;

    private abstract static class TaskSubmissionMixin {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        abstract WorkflowJsonValue result();
    }

    public ViewerWire() {
        var longs =
                new SimpleModule()
                        .addSerializer(Long.class, ToStringSerializer.instance)
                        .addSerializer(Long.TYPE, ToStringSerializer.instance);
        longs.addSerializer(WorkflowJsonValue.class,
                new com.fasterxml.jackson.databind.JsonSerializer<>() {
                    @Override public void serialize(WorkflowJsonValue value,
                            com.fasterxml.jackson.core.JsonGenerator generator, com.fasterxml.jackson.databind.SerializerProvider provider)
                            throws java.io.IOException { generator.writeRawValue(value.toJson()); }
                });
        mapper =
                new ObjectMapper(
                                JsonFactory.builder()
                                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                        .streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder()
                                                .maxNestingDepth(64).maxNumberLength(128).maxStringLength(io.github.leontecristian.workflow.core.service.runtime.RuntimeJson.HISTORY_UTF16_BYTES / 2).build())
                                        .build())
                        .registerModule(longs)
                        .registerModule(new JavaTimeModule())
                        .registerModule(new Jdk8Module())
                        .addMixIn(TaskSubmission.class, TaskSubmissionMixin.class)
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        mapper.enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
                com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
        mapper.setConfig(mapper.getDeserializationConfig().without(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES));
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
            if (result == null || parser.nextToken() != null) throw WorkflowViewerException.invalid();
            return result;
        } catch (WorkflowViewerException e) {
            throw e;
        } catch (com.fasterxml.jackson.core.exc.StreamConstraintsException e) {
            throw new WorkflowViewerException(422,"LIMIT_EXCEEDED");
        } catch (Exception e) {
            throw WorkflowViewerException.invalid();
        }
    }

    public ResponseEntity<String> response(Object value) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json(value));
    }

    public static long decimal(String value, boolean zero) {
        if (value == null || !value.matches(zero ? "0|[1-9][0-9]*" : "[1-9][0-9]*"))
            throw WorkflowViewerException.invalid();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw WorkflowViewerException.invalid();
        }
    }

    public static int limit(String value) {
        long result = value == null ? 50 : decimal(value, false);
        if (result > 100) throw WorkflowViewerException.invalid();
        return (int) result;
    }

    public static long after(String value) {
        return value == null ? 0 : decimal(value, true);
    }

    public static void query(HttpServletRequest request, String... keys) {
        Set<String> allowed = Set.of(keys);
        request.getParameterMap()
                .forEach(
                        (key, values) -> {
                            if (!allowed.contains(key) || values.length != 1)
                                throw WorkflowViewerException.invalid();
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
                if (items.isEmpty()) throw new WorkflowViewerException(422, "LIMIT_EXCEEDED");
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
