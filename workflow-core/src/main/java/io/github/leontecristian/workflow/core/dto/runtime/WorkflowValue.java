package io.github.leontecristian.workflow.core.dto.runtime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
/** Validated typed scalar or immutable JSON snapshot; Java null is never a value token. */
public record WorkflowValue(VariableValueType type, WorkflowLiteral value) {
    public WorkflowValue {
        java.util.Objects.requireNonNull(type); java.util.Objects.requireNonNull(value);
        if (type == VariableValueType.JSON && !(value instanceof WorkflowJsonValue) && value != NullLiteral.INSTANCE)
            throw new IllegalArgumentException("Expected JSON");
        if (value != NullLiteral.INSTANCE && type != VariableValueType.JSON) {
            if (type == VariableValueType.BOOLEAN) {
                if (!(value instanceof BooleanLiteral)) throw new IllegalArgumentException("Expected Boolean");
            } else {
                if (!(value instanceof TextLiteral text)) throw new IllegalArgumentException("Expected text");
                String s = text.value();
                if (!unicode(s) || (type != VariableValueType.STRING && s.codePointCount(0,s.length()) > 128))
                    throw new IllegalArgumentException("Invalid scalar text");
                switch (type) {
                    case STRING -> {
                        if (s.codePointCount(0,s.length()) > 4000 || s.length()*2L > 8000) throw new IllegalArgumentException("String too long");
                    }
                    case LONG -> {
                        if (!s.matches("0|-?[1-9][0-9]*")) throw new IllegalArgumentException("Invalid LONG");
                        s = Long.toString(Long.parseLong(s));
                    }
                    case DECIMAL -> {
                        if (!s.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")) throw new IllegalArgumentException("Invalid DECIMAL");
                        BigDecimal n = new BigDecimal(s);
                        if (n.signum() == 0 && s.startsWith("-")) throw new IllegalArgumentException("Negative zero");
                        n = n.stripTrailingZeros();
                        if (Math.max(n.scale(),0) > 10 || n.precision()-n.scale() > 28) throw new IllegalArgumentException("DECIMAL out of range");
                        s = n.signum() == 0 ? "0" : n.toPlainString();
                    }
                    case DATE -> {
                        if (!s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException("Invalid DATE");
                        LocalDate d = LocalDate.parse(s);
                        if (d.getYear() < 1 || d.getYear() > 9999) throw new IllegalArgumentException("DATE out of range");
                        s = d.toString();
                    }
                    case DATETIME -> {
                        if (!s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]+)?(Z|[+-][0-9]{2}:[0-9]{2})")) throw new IllegalArgumentException("Invalid DATETIME");
                        s = s.replaceFirst("(\\.[0-9]*?)0+(Z|[+-][0-9]{2}:[0-9]{2})$", "$1$2").replace(".Z","Z").replaceFirst("\\.([+-])", "$1");
                        OffsetDateTime t = OffsetDateTime.parse(s);
                        if (Math.abs(t.getOffset().getTotalSeconds()) > 14*3600 || t.getNano()%100 != 0) throw new IllegalArgumentException("Inexact DATETIME");
                        t = t.withOffsetSameInstant(ZoneOffset.UTC);
                        if (t.getYear() < 1 || t.getYear() > 9999) throw new IllegalArgumentException("DATETIME out of range");
                        s = t.toInstant().toString().replaceFirst("(\\.[0-9]*?[1-9])0+Z$", "$1Z");
                    }
                    default -> throw new IllegalArgumentException("Unsupported type");
                }
                value = new TextLiteral(s);
            }
        }
    }
    private static boolean unicode(String s) {
        for (int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if (Character.isHighSurrogate(c)) { if (++i>=s.length() || !Character.isLowSurrogate(s.charAt(i))) return false; }
            else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }
}
