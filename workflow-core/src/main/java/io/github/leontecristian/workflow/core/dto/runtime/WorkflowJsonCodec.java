package io.github.leontecristian.workflow.core.dto.runtime;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.TreeMap;

/** Strict streaming execution-value codec; limits are relative to this value, never its envelope. */
final class WorkflowJsonCodec {
    private static final int VALUE_CHARS = WorkflowJsonValue.MAX_UTF16_BYTES / 2;
    private static final JsonFactory FACTORY = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(WorkflowJsonValue.MAX_DEPTH)
                    .maxNumberLength(128).maxStringLength(32000).build()).build();

    private WorkflowJsonCodec() { }

    static String canonical(String source) {
        if (source == null) throw error(Category.INVALID_INPUT);
        if (source.length() > WorkflowJsonValue.MAX_RAW_UTF8_BYTES || utf8Length(source) > WorkflowJsonValue.MAX_RAW_UTF8_BYTES)
            throw error(Category.LIMIT_EXCEEDED);
        try (JsonParser parser = FACTORY.createParser(source)) {
            JsonToken token = parser.nextToken();
            if (token != JsonToken.START_OBJECT && token != JsonToken.START_ARRAY) throw error(Category.INVALID_INPUT);
            String result = read(parser);
            if (parser.nextToken() != null) throw error(Category.INVALID_INPUT);
            if (utf8Length(result) > WorkflowJsonValue.MAX_UTF8_BYTES) throw error(Category.LIMIT_EXCEEDED);
            return result;
        } catch (com.fasterxml.jackson.core.exc.StreamConstraintsException failure) {
            throw error(Category.LIMIT_EXCEEDED);
        } catch (IOException | ArithmeticException | NumberFormatException failure) {
            throw error(Category.INVALID_INPUT);
        }
    }

    private static String read(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.START_OBJECT) {
            var fields = new TreeMap<String, String>(WorkflowJsonCodec::compareCodePoints);
            long length = 2;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) throw error(Category.INVALID_INPUT);
                String key = parser.currentName(); checkText(key, 256);
                parser.nextToken(); String value = read(parser);
                String quoted = quote(key);
                length += quoted.length() + value.length() + 2; bound(length - 1);
                fields.put(key, value);
                if (fields.size() > 100) throw error(Category.LIMIT_EXCEEDED);
            }
            StringBuilder out = new StringBuilder("{");
            for (var field : fields.entrySet()) {
                if (out.length() > 1) out.append(','); out.append(quote(field.getKey())).append(':').append(field.getValue());
            }
            return out.append('}').toString();
        }
        if (token == JsonToken.START_ARRAY) {
            StringBuilder out = new StringBuilder("["); int count = 0;
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (++count > 1000) throw error(Category.LIMIT_EXCEEDED);
                String value = read(parser); bound(out.length() + value.length() + (count > 1 ? 1 : 0) + 1);
                if (count > 1) out.append(','); out.append(value);
            }
            return out.append(']').toString();
        }
        if (token == JsonToken.VALUE_STRING) { String value = parser.getText(); checkText(value, 16000); return quote(value); }
        if (token == JsonToken.VALUE_TRUE) return "true";
        if (token == JsonToken.VALUE_FALSE) return "false";
        if (token == JsonToken.VALUE_NULL) return "null";
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
            String text = parser.getText(); if (text.length() > 128) throw error(Category.LIMIT_EXCEEDED);
            BigDecimal value = new BigDecimal(text), normalized = value.stripTrailingZeros();
            if (Math.max(normalized.scale(), 0) > 10 || (long) normalized.precision() - normalized.scale() > 28)
                throw error(Category.INVALID_INPUT);
            return value.toString();
        }
        throw error(Category.INVALID_INPUT);
    }

    private static void bound(long length) { if (length > VALUE_CHARS) throw error(Category.LIMIT_EXCEEDED); }
    private static long utf8Length(String value) {
        long bytes = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            bytes += Character.isHighSurrogate(c) ? 4 : Character.isLowSurrogate(c) ? 0 : c <= 127 ? 1 : c <= 2047 ? 2 : 3;
        }
        return bytes;
    }
    static void requireDepth(String source, int maxDepth) {
        try (JsonParser parser = FACTORY.createParser(source)) {
            int depth = 0;
            for (JsonToken token; (token = parser.nextToken()) != null;) {
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                    if (++depth > maxDepth) throw error(Category.LIMIT_EXCEEDED);
                } else if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) depth--;
            }
        } catch (IOException failure) { throw error(Category.INVALID_INPUT); }
    }
    private static void checkText(String text, int limit) {
        if (text.codePointCount(0, text.length()) > limit) throw error(Category.LIMIT_EXCEEDED);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) throw error(Category.INVALID_INPUT);
            } else if (Character.isLowSurrogate(c)) throw error(Category.INVALID_INPUT);
        }
    }
    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int)c));
            else out.append(c);
        }
        bound(out.length() + 1); return out.append('"').toString();
    }
    private static int compareCodePoints(String a, String b) {
        int ai = 0, bi = 0;
        while (ai < a.length() && bi < b.length()) {
            int ac = a.codePointAt(ai), bc = b.codePointAt(bi);
            if (ac != bc) return Integer.compare(ac, bc);
            ai += Character.charCount(ac); bi += Character.charCount(bc);
        }
        return Integer.compare(a.length() - ai, b.length() - bi);
    }
    private static WorkflowExecutionException error(Category category) { return new WorkflowExecutionException(category); }
}
