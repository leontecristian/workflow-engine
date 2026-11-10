package io.github.leontecristian.workflow.web.service.runtime;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmission;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmissionPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Private HTTP/evidence codec; never replaces the application's or core's ObjectMapper. */
public class RuntimeWire {
    private static final int SUBMISSION_PAGE_BYTES = 1024 * 1024;
    private static final JsonFactory COMPLETION_JSON = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).maxNumberLength(128).build())
            .build();
    private final io.github.leontecristian.workflow.web.service.instance.ViewerWire codec =
            new io.github.leontecristian.workflow.web.service.instance.ViewerWire();
    public String json(Object value) { return codec.json(value); }
    public JsonNode tree(String value) {
        try { return codec.tree(value); }
        catch (io.github.leontecristian.workflow.web.exception.instance.WorkflowViewerException failure) {
            throw new WorkflowRuntimeException(failure.status(), failure.code());
        }
    }

    public ResponseEntity<String> response(Object value) {
        return jsonResponse(json(value));
    }

    private static ResponseEntity<String> jsonResponse(String json) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }

    public record CompletionBody(JsonNode fields, WorkflowJsonValue result) {}

    public CompletionBody completionBody(HttpServletRequest request, String... fields) {
        String source = raw(request, 256 * 1024);
        Set<String> allowed = Set.of(fields);
        var metadata = JsonNodeFactory.instance.objectNode();
        String resultJson = null;
        // Slice the original object token. A tree would interpret numeric tokens before the exact codec.
        try (var parser = COMPLETION_JSON.createParser(source)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw WorkflowRuntimeException.invalid();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String key = parser.currentName();
                JsonToken value = parser.nextToken();
                if ("result".equals(key)) {
                    if (value != JsonToken.START_OBJECT) throw WorkflowRuntimeException.invalid();
                    int start = (int) parser.currentTokenLocation().getCharOffset();
                    parser.skipChildren();
                    int end = (int) parser.currentLocation().getCharOffset();
                    resultJson = source.substring(start, end);
                } else if (allowed.contains(key) && value == JsonToken.VALUE_STRING) {
                    metadata.put(key, parser.getText());
                } else if (allowed.contains(key) && value == JsonToken.VALUE_NULL) {
                    metadata.putNull(key);
                } else {
                    throw WorkflowRuntimeException.invalid();
                }
            }
            if (parser.currentToken() != JsonToken.END_OBJECT || parser.nextToken() != null)
                throw WorkflowRuntimeException.invalid();
        } catch (StreamConstraintsException limit) {
            throw WorkflowExecutionException.resultLimit("The completion JSON exceeds the depth or numeric token limit.");
        } catch (IOException invalid) {
            throw WorkflowRuntimeException.invalid();
        }
        return new CompletionBody(metadata, resultJson == null ? null : CompleteWorkflowTask.parseResult(resultJson));
    }

    public ResponseEntity<String> submissionPage(TaskSubmissionPage page) {
        String prefix = "{\"items\":[";
        int bytes = prefix.length();
        List<String> rows = new ArrayList<>();
        Long next = page.nextAfterTaskId();
        for (int index = 0; index < page.items().size(); index++) {
            TaskSubmission submission = page.items().get(index);
            String row = json(submission);
            int rowBytes = utf8Length(row) + (index == 0 ? 0 : 1);
            Long candidate = index + 1 == page.items().size() ? page.nextAfterTaskId() : Long.valueOf(submission.taskId());
            if (bytes + rowBytes + submissionPageEnd(candidate).length() > SUBMISSION_PAGE_BYTES) {
                if (rows.isEmpty()) throw new WorkflowRuntimeException(422, "LIMIT_EXCEEDED");
                next = page.items().get(index - 1).taskId();
                break;
            }
            rows.add(row);
            bytes += rowBytes;
        }
        return jsonResponse(prefix + String.join(",", rows) + submissionPageEnd(next));
    }

    private static String submissionPageEnd(Long next) {
        return "],\"nextAfterTaskId\":" + (next == null ? "null" : "\"" + next + "\"") + "}";
    }

    static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    public String raw(HttpServletRequest request, int limit) {
        if (request.getHeader("Content-Encoding") != null
                && !"identity".equalsIgnoreCase(request.getHeader("Content-Encoding")))
            throw new WorkflowRuntimeException(415, "UNSUPPORTED_MEDIA_TYPE");
        String type = request.getContentType();
        if (type == null
                || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(type)))
            throw new WorkflowRuntimeException(415, "UNSUPPORTED_MEDIA_TYPE");
        if (request.getContentLengthLong() > limit)
            throw new WorkflowRuntimeException(413, "PAYLOAD_TOO_LARGE");
        try {
            byte[] bytes = request.getInputStream().readNBytes(limit + 1);
            if (bytes.length > limit) throw new WorkflowRuntimeException(413, "PAYLOAD_TOO_LARGE");
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (WorkflowRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw WorkflowRuntimeException.invalid();
        }
    }

    public JsonNode body(HttpServletRequest request, String... fields) {
        JsonNode node = tree(raw(request, 16 * 1024));
        if (!node.isObject()) throw WorkflowRuntimeException.invalid();
        Set<String> allowed = Set.of(fields);
        node.fieldNames()
                .forEachRemaining(
                        key -> {
                            if (!allowed.contains(key)) throw WorkflowRuntimeException.invalid();
                        });
        return node;
    }

    public static String text(JsonNode node, String key, boolean nullable) {
        JsonNode value = node.get(key);
        if (nullable && (value == null || value.isNull())) return null;
        if (value == null || !value.isTextual()) throw WorkflowRuntimeException.invalid();
        if (!unicode(value.textValue())) throw WorkflowRuntimeException.invalid();
        return value.textValue();
    }

    public static long decimal(String value, boolean zero) {
        if (value == null || !value.matches(zero ? "0|[1-9][0-9]*" : "[1-9][0-9]*"))
            throw WorkflowRuntimeException.invalid();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw WorkflowRuntimeException.invalid();
        }
    }

    public static long id(JsonNode node, String key, boolean zero) {
        return decimal(text(node, key, false), zero);
    }
    public static java.util.UUID uuid(JsonNode node) {
        String value = text(node, "commandId", false);
        try {
            var id = java.util.UUID.fromString(value);
            if (!id.toString().equals(value)) throw WorkflowRuntimeException.invalid();
            return id;
        } catch (IllegalArgumentException failure) { throw WorkflowRuntimeException.invalid(); }
    }

    public static int limit(String value) {
        long result = value == null ? 50 : decimal(value, false);
        if (result > 100) throw WorkflowRuntimeException.invalid();
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
                                throw WorkflowRuntimeException.invalid();
                        });
    }

    public <T> Map<String, Object> page(
            List<T> rows, Long sourceNext, String cursorName, ToLongFunction<T> cursor) {
        try { return codec.page(rows, sourceNext, cursorName, cursor); }
        catch (io.github.leontecristian.workflow.web.exception.instance.WorkflowViewerException failure) {
            throw new WorkflowRuntimeException(failure.status(), failure.code());
        }
    }

    public static boolean unicode(String value) {
        if (value == null) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index)))
                    return false;
            } else if (Character.isLowSurrogate(character)) return false;
        }
        return true;
    }
}
