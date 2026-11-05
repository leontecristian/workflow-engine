package io.github.leontecristian.workflow.core.service.expression;

import io.github.leontecristian.workflow.core.exception.expression.ExpressionException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.LongConsumer;
import static io.github.leontecristian.workflow.core.exception.expression.ExpressionException.Kind.LIMIT;

/** Allocation and work limits only; SpEL owns numeric results and conversions. */
final class NumericWork {
    private NumericWork() { }
    private static BigDecimal estimate(Number number) {
        if (number instanceof BigDecimal decimal) return decimal;
        if (number instanceof BigInteger integer) return new BigDecimal(integer);
        if (number instanceof Double || number instanceof Float) {
            double value = number.doubleValue();
            return Double.isFinite(value) ? BigDecimal.valueOf(value) : BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(number.longValue());
    }
    static void capacity(Number number) {
        BigDecimal value = estimate(number);
        if (value.precision() > 256 || Math.abs((long) value.scale()) > 256)
            throw new ExpressionException(LIMIT);
    }
    private static long digits(BigDecimal value) {
        return Math.max(value.precision(), value.precision() - (long) value.scale()) + Math.max(0, value.scale());
    }
    static void check(String kind, Number left, Number right, LongConsumer charge) {
        capacity(left);
        if (right != null) capacity(right);
        BigDecimal a = estimate(left), b = right == null ? null : estimate(right);
        long da = digits(a), db = b == null ? 0 : digits(b);
        charge.accept(kind.equals("OpMultiply") ? da * db : da + db);
        if (kind.equals("OperatorPower")) {
            double exponent = right.doubleValue();
            if (!Double.isFinite(exponent) || Math.abs(exponent) > 1024) throw new ExpressionException(LIMIT);
            long power = (long) Math.ceil(Math.abs(exponent));
            if (a.precision() * power > 256 || Math.abs((long) a.scale()) * power > 256) throw new ExpressionException(LIMIT);
            charge.accept(da * da * Math.max(1, power));
        } else if (kind.equals("OpMultiply")) {
            if (a.precision() + b.precision() > 256 || Math.abs((long) a.scale() + b.scale()) > 256) throw new ExpressionException(LIMIT);
        } else if (kind.equals("OpDivide") || kind.equals("OpModulus")) {
            if (da + db + Math.abs((long) a.scale()) + Math.abs((long) b.scale()) > 256) throw new ExpressionException(LIMIT);
        } else if ((kind.equals("OpPlus") || kind.equals("OpMinus")) && b != null && Math.max(da, db) + 1 > 256) {
            throw new ExpressionException(LIMIT);
        }
    }
}
