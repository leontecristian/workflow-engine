package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.NumericNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Engine-local exact canonical form. No floating-point or exponent expansion is used. */
public final class DefinitionContentCanonicalizer {
    public static final String IDENTIFIER = "wf-json-c14n-v2";

    private DefinitionContentCanonicalizer() { }

    public static byte[] hash(String text) {
        try { return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 unavailable", failure); }
    }

    public static String hex(byte[] bytes) { return bytes == null ? null : HexFormat.of().formatHex(bytes); }

    /** Internal core codec reuse; not a host serialization policy. */
    public static String canonical(JsonNode node) {
        return canonical(node,8*1024*1024);
    }
    public static String canonical(JsonNode node,int maxChars) {
        return canonical(node,maxChars,Integer.MAX_VALUE);
    }
    public static String canonical(JsonNode node,int maxChars,int maxUtf8Bytes) {
        Output out = new Output(maxChars,maxUtf8Bytes);
        append(node, out);
        return out.toString();
    }

    private static final class Output {
        private final StringBuilder value=new StringBuilder();
        private final int limit;
        private final int byteLimit;
        private long bytes;
        Output(int limit,int byteLimit) { this.limit=limit; this.byteLimit=byteLimit; }
        Output append(char c) {
            // Valid surrogate pairs are charged in full at their leading code unit.
            int size=Character.isHighSurrogate(c) ? 4 : Character.isLowSurrogate(c) ? 0 : c<=127 ? 1 : c<=2047 ? 2 : 3;
            check(1,size); value.append(c); return this;
        }
        Output append(String text) { check(text.length(),text.getBytes(StandardCharsets.UTF_8).length); value.append(text); return this; }
        private void check(int count,int encodedBytes) {
            if ((long)value.length()+count>limit || bytes+encodedBytes>byteLimit) throw new io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException(
                    io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.LIMIT_EXCEEDED);
            bytes+=encodedBytes;
        }
        @Override public String toString() { return value.toString(); }
    }

    private static void append(JsonNode node, Output out) {
        if (node.isObject()) {
            out.append('{');
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.sort(DefinitionContentCanonicalizer::compareScalars);
            boolean first = true;
            for (String name : names) {
                if (!first) out.append(',');
                first = false;
                quote(name, out); out.append(':'); append(node.get(name), out);
            }
            out.append('}');
        } else if (node.isArray()) {
            out.append('[');
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) out.append(',');
                append(node.get(i), out);
            }
            out.append(']');
        } else if (node.isTextual()) quote(node.textValue(), out);
        else if (node.isNumber()) out.append(node instanceof ExactNumber number ? number.canonical : (node.isIntegralNumber() ? node.bigIntegerValue().toString() : node.decimalValue().toString()));
        else out.append(node.isNull() ? "null" : node.booleanValue() ? "true" : "false");
    }

    private static int compareScalars(String a, String b) {
        int ai = 0, bi = 0;
        while (ai < a.length() && bi < b.length()) {
            int ac = a.codePointAt(ai), bc = b.codePointAt(bi);
            if (ac != bc) return Integer.compare(ac, bc);
            ai += Character.charCount(ac); bi += Character.charCount(bc);
        }
        return Integer.compare(a.length() - ai, b.length() - bi);
    }

    private static void quote(String value, Output out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
            else out.append(c);
        }
        out.append('"');
    }

    /** Scale-preserving numeric token inside a bounded definition document. */
    static final class ExactNumber extends NumericNode {
        private final String canonical;
        ExactNumber(String token) { canonical = new BigDecimal(token).toString(); }
        @Override public JsonToken asToken() { return JsonToken.VALUE_NUMBER_FLOAT; }
        @Override public JsonParser.NumberType numberType() { return JsonParser.NumberType.BIG_DECIMAL; }
        @Override public Number numberValue() { return decimalValue(); }
        @Override public int intValue() { return decimalValue().intValue(); }
        @Override public long longValue() { return decimalValue().longValue(); }
        @Override public double doubleValue() { return decimalValue().doubleValue(); }
        @Override public BigDecimal decimalValue() { return new BigDecimal(canonical); }
        @Override public BigInteger bigIntegerValue() { return decimalValue().toBigInteger(); }
        @Override public boolean canConvertToInt() {
            try { decimalValue().intValueExact(); return true; } catch (ArithmeticException | NumberFormatException e) { return false; }
        }
        @Override public boolean canConvertToLong() {
            try { decimalValue().longValueExact(); return true; } catch (ArithmeticException | NumberFormatException e) { return false; }
        }
        @Override public String asText() { return canonical; }
        @Override public void serialize(JsonGenerator generator, SerializerProvider provider) throws IOException { generator.writeNumber(canonical); }
        @Override public boolean equals(Object other) { return other instanceof ExactNumber n && canonical.equals(n.canonical); }
        @Override public int hashCode() { return canonical.hashCode(); }
    }
}
