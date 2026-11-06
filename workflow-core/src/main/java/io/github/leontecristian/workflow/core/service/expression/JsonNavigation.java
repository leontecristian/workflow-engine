package io.github.leontecristian.workflow.core.service.expression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import io.github.leontecristian.workflow.core.exception.expression.ExpressionException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.LongConsumer;
import static io.github.leontecristian.workflow.core.exception.expression.ExpressionException.Kind.EVALUATION;

/** Private detached trees, without reflective property/index access. */
final class JsonNavigation {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));
    private JsonNavigation() { }
    static JsonNode parse(String text) {
        try { return JSON.readTree(text); }
        catch (java.io.IOException failure) { throw new ExpressionException(EVALUATION); }
    }
    static Object project(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isContainerNode()) return node;
        if (node.isNumber()) return node.decimalValue();
        if (node.isTextual()) return node.textValue();
        return node.booleanValue();
    }
    static String encode(Object value) {
        if (value instanceof JsonNode node) return io.github.leontecristian.workflow.core.service.definition.DefinitionContentCanonicalizer.canonical(node);
        try { return JSON.writeValueAsString(value); }
        catch (java.io.IOException failure) { throw new ExpressionException(EVALUATION); }
    }
    static BigDecimal index(Object value, LongConsumer work) {
        if (!(value instanceof Number number)) throw new ExpressionException(EVALUATION);
        if ((number instanceof Double || number instanceof Float) && !Double.isFinite(number.doubleValue())) throw new ExpressionException(EVALUATION);
        BigDecimal index = number instanceof BigDecimal d ? d : number instanceof BigInteger i ? new BigDecimal(i)
                : number instanceof Double || number instanceof Float ? BigDecimal.valueOf(number.doubleValue()) : BigDecimal.valueOf(number.longValue());
        work.accept(index.precision());
        if (index.signum() != 0 && index.stripTrailingZeros().scale() > 0) throw new ExpressionException(EVALUATION);
        return index.signum() == 0 ? BigDecimal.ZERO : index.stripTrailingZeros();
    }
    static boolean equal(Object a, Object b, LongConsumer work) {
        if (a == null || b == null) return a == b;
        if (!(a instanceof JsonNode left) || !(b instanceof JsonNode right)) throw new ExpressionException(EVALUATION);
        return compare(left,right,work);
    }
    private static boolean compare(JsonNode a, JsonNode b, LongConsumer work) {
        work.accept(1);
        if (a.isNumber() && b.isNumber()) {
            work.accept((long)a.decimalValue().precision()+b.decimalValue().precision());
            return a.decimalValue().equals(b.decimalValue());
        }
        if (a.getNodeType()!=b.getNodeType()) return false;
        if (a.isObject()) {
            if (a.size()!=b.size()) return false;
            var names=a.fieldNames();
            while (names.hasNext()) { String key=names.next(); work.accept(key.length()); var other=b.get(key);
                if (other==null || !compare(a.get(key),other,work)) return false; }
            return true;
        }
        if (a.isArray()) {
            if (a.size()!=b.size()) return false;
            for (int i=0;i<a.size();i++) if (!compare(a.get(i),b.get(i),work)) return false;
            return true;
        }
        if (a.isTextual()) {
            String left=a.textValue(), right=b.textValue();
            if(left.length()!=right.length()) return false;
            for(int i=0;i<left.length();i++) { work.accept(2); if(left.charAt(i)!=right.charAt(i)) return false; }
            return true;
        }
        return a.equals(b);
    }
}
