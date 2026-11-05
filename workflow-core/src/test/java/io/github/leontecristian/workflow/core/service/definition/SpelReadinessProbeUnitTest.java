package io.github.leontecristian.workflow.core.service.definition;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.ast.BooleanLiteral;
import org.springframework.expression.spel.ast.OpAnd;
import org.springframework.expression.spel.ast.SpelNodeImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Isolated dependency probes for F008 readiness; this is not a production evaluator. */
class SpelReadinessProbeUnitTest {
    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Test void numericBehavior() {
        var context = new StandardEvaluationContext();
        context.setVariable("vars", Map.of("max", Long.MAX_VALUE, "one", new BigDecimal("1"),
                "two", new BigDecimal("2"), "tenth", new BigDecimal("0.1")));
        assertThat(parser.parseExpression("#vars['max'] + 1").getValue(context)).isEqualTo(Long.MIN_VALUE);
        assertThat(parser.parseExpression("#vars['one'] / #vars['two']").getValue(context)).isEqualTo(new BigDecimal("0"));
        assertThat(parser.parseExpression("0.1 + 0.2").getValue(context)).isEqualTo(0.30000000000000004d);
        assertThat(parser.parseExpression("#vars['tenth'] + 0.2").getValue(context)).isEqualTo(new BigDecimal("0.3"));
        assertThat(parser.parseExpression("5 / 2").getValue(context)).isEqualTo(2);
        assertThat(parser.parseExpression("false and #vars['absent'] > 0").getValue(context)).isEqualTo(false);
    }

    @Test void inventoryAstForms() {
        for (String source : new String[]{"#vars.quantity > 10", "#vars['quantity'] > 10L",
                "#vars?.quantity == null", "#vars?.['quantity'] == null", "true ? 1 : 2",
                "#vars.quantity ?: 0", "1 + 2 * 3 / 4 % 2 ^ 2", "-1", "!false", "'a' < 'b'",
                "#vars.quantity between {1, 10}", "#vars.quantity matches '.*'"}) {
            SpelExpression expression = (SpelExpression) parser.parseExpression(source);
            System.out.println(source + " => " + inventory(expression.getAST()));
        }
    }

    @Test void currentParserHasNoOperationBudgetApi() {
        assertThat(SpelParserConfiguration.class.getPackage().getImplementationVersion()).isEqualTo("6.2.6");
        assertThat(SpelParserConfiguration.class.getMethods()).noneMatch(m -> m.getName().equals("getMaximumOperations"));
    }

    @Test void detachedTemporalAndNullSemantics() {
        var context = new StandardEvaluationContext();
        context.setVariable("date", java.time.LocalDate.parse("2026-11-05"));
        context.setVariable("later", java.time.LocalDate.parse("2026-11-06"));
        context.setVariable("instant", java.time.Instant.parse("2026-11-05T12:00:00Z"));
        context.setVariable("same", java.time.OffsetDateTime.parse("2026-11-05T14:00:00+02:00").toInstant());
        assertThat(parser.parseExpression("#date < #later").getValue(context)).isEqualTo(true);
        assertThat(parser.parseExpression("#instant == #same").getValue(context)).isEqualTo(true);
        assertThat(parser.parseExpression("null < 0").getValue(context)).isEqualTo(true);
        assertThat(parser.parseExpression("null == null").getValue(context)).isEqualTo(true);
        assertThat(parser.parseExpression("'' ?: 'fallback'").getValue(context)).isEqualTo("fallback");
        assertThatThrownBy(() -> parser.parseExpression("#date < #instant").getValue(context))
                .isInstanceOf(org.springframework.expression.EvaluationException.class);
    }

    @Test void localNodeGuardsChargeBeforeWorkAndPreserveShortCircuit() {
        var state = new ProbeState(2);
        var left = new Guard(new BooleanLiteral("false", 0, 5, false));
        var skipped = new Guard(new BooleanLiteral("true", 10, 14, true));
        var root = new Guard(new OpAnd(0, 14, left, skipped));
        assertThat(root.getValue(state)).isEqualTo(false);
        assertThat(state.remaining).isZero();
        assertThatThrownBy(() -> root.getValue(state)).isInstanceOf(IllegalStateException.class);
        var insufficient = new ProbeState(1);
        assertThatThrownBy(() -> root.getValue(insufficient)).isInstanceOf(IllegalStateException.class);
        assertThat(insufficient.remaining).isZero();
    }

    @Test void localNodeGuardEnforcesTenThousandBoundary() {
        var state = new ProbeState(10_000);
        var literal = new Guard(new BooleanLiteral("true", 0, 4, true));
        for (int i = 0; i < 10_000; i++) assertThat(literal.getValue(state)).isEqualTo(true);
        assertThat(state.remaining).isZero();
        assertThatThrownBy(() -> literal.getValue(state)).isInstanceOf(IllegalStateException.class);
    }

    @Test void nativeNumericOperationsBypassTheOperatorOverloader() {
        var context = new StandardEvaluationContext();
        context.setOperatorOverloader(new org.springframework.expression.OperatorOverloader() {
            @Override public boolean overridesOperation(org.springframework.expression.Operation op, Object left, Object right) {
                throw new AssertionError("Numeric operations must not be assumed to reach the hook");
            }
            @Override public Object operate(org.springframework.expression.Operation op, Object left, Object right) {
                throw new AssertionError("Unexpected overloader call");
            }
        });
        context.setVariable("one", new BigDecimal("1"));
        context.setVariable("two", new BigDecimal("2"));
        assertThat(parser.parseExpression("#one / #two").getValue(context)).isEqualTo(BigDecimal.ZERO);
        assertThat(parser.parseExpression("9223372036854775807L + 1L").getValue(context)).isEqualTo(Long.MIN_VALUE);
    }

    @Test void exactnessReferenceDistinguishesNativeTruncationRoundingAndPromotion() {
        assertThat(new BigDecimal(0.5d)).isEqualByComparingTo("0.5");
        assertThat(new BigDecimal(0.1d)).isNotEqualByComparingTo("0.1");
        assertThat(new BigDecimal(0.1f)).isNotEqualByComparingTo("0.1");
        var context = new StandardEvaluationContext();
        context.setVariable("large", 9_007_199_254_740_993L);
        assertThat(parser.parseExpression("#large == 9007199254740992.0").getValue(context)).isEqualTo(true);
        assertThat(new BigDecimal(9_007_199_254_740_993L)).isNotEqualByComparingTo(new BigDecimal((double)9_007_199_254_740_993L));
        context.setVariable("scaled", new BigDecimal("1.0"));
        context.setVariable("two", new BigDecimal("2"));
        assertThat(parser.parseExpression("#scaled / #two").getValue(context)).isEqualTo(new BigDecimal("0.5"));
        assertThat(BigDecimal.ONE.divide(new BigDecimal("2"))).isEqualByComparingTo("0.5");
        assertThat(new java.math.BigInteger("5").divide(new java.math.BigInteger("2"))).isEqualTo(new java.math.BigInteger("2"));
        assertThatThrownBy(() -> BigDecimal.ONE.divide(new BigDecimal("3"))).isInstanceOf(ArithmeticException.class);
    }

    @Test void parserRecursionAndLiteralBoundariesOnCurrentJdk() {
        assertThat(parser.parseExpression("(".repeat(32) + "true" + ")".repeat(32)).getValue()).isEqualTo(true);
        assertThat(parser.parseExpression("!".repeat(64) + "true").getValue()).isEqualTo(true);
        assertThat(parser.parseExpression("'😀' == '\uD83D\uDE00'").getValue()).isEqualTo(true);
        assertThat(parser.parseExpression("0x10 == 16 and 16L == 16").getValue()).isEqualTo(true);
    }

    private static final class ProbeState extends ExpressionState {
        private int remaining;
        ProbeState(int remaining) { super(new StandardEvaluationContext()); this.remaining = remaining; }
        void charge() {
            if (remaining == 0) throw new IllegalStateException("Operation limit");
            remaining--;
        }
    }

    private static final class Guard extends SpelNodeImpl {
        private final SpelNodeImpl delegate;
        Guard(SpelNodeImpl delegate) {
            super(delegate.getStartPosition(), delegate.getEndPosition());
            this.delegate = delegate;
        }
        @Override public TypedValue getValueInternal(ExpressionState state) {
            ((ProbeState) state).charge();
            return delegate.getValueInternal(state);
        }
        @Override public String toStringAST() { return delegate.toStringAST(); }
    }

    private String inventory(SpelNode node) {
        var result = new StringBuilder(node.getClass().getSimpleName());
        if (node.getChildCount() > 0) {
            result.append('(');
            for (int i = 0; i < node.getChildCount(); i++) {
                if (i > 0) result.append(',');
                result.append(inventory(node.getChild(i)));
            }
            result.append(')');
        }
        return result.toString();
    }
}
