package io.github.leontecristian.workflow.core.service.expression;

import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.NullLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.exception.expression.ExpressionException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.github.leontecristian.workflow.core.exception.expression.ExpressionException.Kind.EVALUATION;
import static io.github.leontecristian.workflow.core.exception.expression.ExpressionException.Kind.INVALID;
import static io.github.leontecristian.workflow.core.exception.expression.ExpressionException.Kind.LIMIT;
import static io.github.leontecristian.workflow.core.exception.expression.ExpressionException.Kind.UNINITIALIZED;
import static io.github.leontecristian.workflow.core.exception.expression.ExpressionException.Kind.UNSUPPORTED;

class RestrictedExpressionUnitTest {
    private final RestrictedExpressionService evaluator = new RestrictedExpressionService();
    private final Map<String, ActionField> fields = Map.of(
            "n", new ActionField("n", VariableValueType.LONG, false, true),
            "d", new ActionField("d", VariableValueType.DECIMAL, false, true),
            "s", new ActionField("s", VariableValueType.STRING, false, true),
            "b", new ActionField("b", VariableValueType.BOOLEAN, false, true),
            "a.b", new ActionField("a.b", VariableValueType.STRING, false, false),
            "class", new ActionField("class", VariableValueType.STRING, false, false));
    private boolean eval(String source, Map<String, WorkflowValue> state) {
        return evaluator.evaluate("a".repeat(64), "route", source, fields, state, new ExpressionAllowance()).result();
    }
    private WorkflowValue value(VariableValueType type, String value) { return new WorkflowValue(type, new TextLiteral(value)); }
    private void fails(String source, Map<String, WorkflowValue> state, ExpressionException.Kind kind) {
        assertThatThrownBy(() -> eval(source, state)).isInstanceOfSatisfying(ExpressionException.class, e -> assertThat(e.kind()).isEqualTo(kind));
    }
    @ParameterizedTest @ValueSource(strings = {"true", "1 + 2 * 3 == 7", "5 / 2 == 2", "-5 / 2 == -2",
            "0.5 + 0.25 == 0.75", "0x10 eq 16L", "2 ^ 3 == 8", "'d' - 3 == 'a'", "'ab' * 2 == 'abab'",
            "null < 0", "null == null", "false < true", "'a' < 'b'", "'' ?: true", "true ? true : false",
            "false and #vars.n > 0 or true", "true or #vars?.['n'] == null", "true ? true : (1 / 0 > 0)",
            "5 between {1, 10}", "'T(java.lang.Runtime)' == 'T(java.lang.Runtime)'"})
    void standardGoldenCases(String source) { assertThat(eval(source, Map.of())).isTrue(); }

    @Test void nativeNumericSemantics() {
        assertThat(eval("0.1 + 0.2 == 0.3", Map.of())).isFalse();
        assertThat(eval("9223372036854775807L + 1 < 0", Map.of())).isTrue();
        assertThat(eval("2147483647 + 1 < 0", Map.of())).isTrue();
        assertThat(eval("9007199254740993L == 9007199254740992.0", Map.of())).isTrue();
        assertThat(eval("4 ^ 0.5 == 2", Map.of())).isTrue();
        assertThat(eval("1.0 / 0.0 > 0", Map.of())).isTrue();
        assertThat(eval("0.0 / 0.0 == 0.0 / 0.0", Map.of())).isFalse();
        assertThat(eval("'a' - 98 == '\uFFFF'", Map.of())).isTrue();
        fails("1 / 0 == 1", Map.of(), EVALUATION);
    }
    @Test void initializedNullAbsentAndReadTracing() {
        fails("#vars.n == null", Map.of(), UNINITIALIZED);
        fails("#vars?.['n'] == null", Map.of(), UNINITIALIZED);
        assertThat(eval("#vars.n == null", Map.of("n", new WorkflowValue(VariableValueType.LONG, NullLiteral.INSTANCE)))).isTrue();
        var evaluation = evaluator.evaluate("hash", "r", "#vars.n + #vars['n'] == 4 and (true or #vars.d > 0)", fields,
                Map.of("n", value(VariableValueType.LONG, "2")), new ExpressionAllowance());
        assertThat(evaluation.result()).isTrue();
        assertThat(evaluation.reads()).extracting(r -> r.variableKey()).containsExactly("n", "n");
        assertThat(eval("#vars['a.b'] == 'x' and #vars.class == 'x'", Map.of("a.b", value(VariableValueType.STRING, "x"), "class", value(VariableValueType.STRING, "x")))).isTrue();
        fails("false ? true : 0", Map.of(), EVALUATION);
    }
    @Test void exactMixedDecimalArithmeticAndNativeRoundedDivision() {
        var state = Map.of("d", value(VariableValueType.DECIMAL, "1"), "n", value(VariableValueType.LONG, "2"));
        assertThat(eval("#vars.d + #vars.n == 3", state)).isTrue();
        assertThat(eval("#vars.d / #vars.n == 0", state)).isTrue();
        assertThat(eval("#vars.d / #vars.n == #vars.d - 1.0", Map.of("d", value(VariableValueType.DECIMAL, "2"), "n", value(VariableValueType.LONG, "2")))).isTrue();
    }
    @Test void decimalVariablesUseBigDecimal() {
        var state = Map.of("d", value(VariableValueType.DECIMAL, "0.1"));
        assertThat(eval("#vars.d + #vars.d + #vars.d == 0.3", state)).isTrue();
        assertThat(eval("#vars.d ^ 0.5 == 1",state)).isTrue();
    }
    @ParameterizedTest @ValueSource(strings = {"#facts.x == 1", "#root == null", "#this == null", "#vars == null",
            "#vars.n.class == null", "#vars['n']['x'] == 1", "#vars[#vars.s] == 1", "#vars.n.toString() == '1'",
            "@environment == null", "T(java.lang.System).currentTimeMillis() > 0", "new java.lang.String('x') == 'x'",
            "#vars.n = 1", "#vars.n++ > 0", "#f()", "'a' matches '.*'", "{true}[0]", "#vars.missing == 1",
            "false and T(java.lang.Runtime).getRuntime() != null", "#vars.s > #vars.n", "'true' and true", "1", "null",
            "#vars.s.?[true] == null", "#vars.n instanceof T(java.lang.Long)"})
    void unsafeOrStaticallyInvalidSourceCannotPublish(String source) {
        assertThatThrownBy(() -> evaluator.validate(source, fields)).isInstanceOfSatisfying(ExpressionException.class, e -> assertThat(e.kind()).isEqualTo(INVALID));
    }
    @Test void boundsAndSharedAllowance() {
        fails("2 ^ 1025 == 1", Map.of(), LIMIT);
        fails("'a' * 2147483647 == ''", Map.of(), LIMIT);
        fails("#vars.s + #vars.s == ''", Map.of("s", value(VariableValueType.STRING, "x".repeat(4000))), LIMIT);
        assertThatThrownBy(() -> evaluator.validate("(".repeat(33) + "true" + ")".repeat(33), fields)).isInstanceOf(ExpressionException.class);
        assertThatThrownBy(() -> evaluator.validate("!".repeat(65) + "true", fields)).isInstanceOf(ExpressionException.class);
        assertThatThrownBy(() -> evaluator.validate("'" + "x".repeat(2000) + "' == ''", fields)).isInstanceOf(ExpressionException.class);
        var allowance = new ExpressionAllowance();
        for (int i = 0; i < 500; i++) evaluator.evaluate("h", "r", "true", fields, Map.of(), allowance);
        assertThatThrownBy(() -> evaluator.evaluate("h", "r", "true", fields, Map.of(), allowance)).isInstanceOfSatisfying(ExpressionException.class, e -> assertThat(e.kind()).isEqualTo(LIMIT));
    }
    @Test void cacheEvictionAndParallelValuesAreIsolated() throws Exception {
        for (int i = 0; i < 150; i++) evaluator.evaluate("h" + i, "r", "#vars.n == 1", fields, Map.of("n", value(VariableValueType.LONG, "1")), new ExpressionAllowance());
        assertThat(evaluator.cachedPlans()).isEqualTo(128);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 40; i++) {
                final int n = i;
                futures.add(executor.submit(() -> eval("#vars.n % 2 == 0", Map.of("n", value(VariableValueType.LONG, Integer.toString(n))))));
            }
            for (int i = 0; i < futures.size(); i++) assertThat(futures.get(i).get()).isEqualTo(i % 2 == 0);
        } finally { executor.shutdownNow(); }
    }
    @Test void actualWorkAllowsExactlyTenThousandAndRejectsTheNextWork() {
        String expression="(#vars.s + '' == #vars.s) and true";
        assertThat(eval(expression, Map.of("s",value(VariableValueType.STRING,"x".repeat(2497))))).isTrue();
        fails(expression,Map.of("s",value(VariableValueType.STRING,"x".repeat(2498))),LIMIT);
        assertThat(eval("true or (0.1 == 0.2)",Map.of())).isTrue();
    }
    @Test void unverifiedDependenciesCannotSilentlyReuseTheProfile() {
        RestrictedExpressionService.requireVersion("6.2.6");
        for(String version:List.of("6.2.7","7.0.10","unknown"))
            assertThatThrownBy(()->RestrictedExpressionService.requireVersion(version)).isInstanceOfSatisfying(ExpressionException.class,e->assertThat(e.kind()).isEqualTo(UNSUPPORTED));
    }
    @Test void betweenReadsBothBoundsButSkipsItsUpperComparisonWhenTheLowerComparisonFails() {
        var state=Map.of("n",value(VariableValueType.LONG,"9007199254740993"));
        assertThat(eval("!(#vars.n between {9007199254740994L, 9007199254740992.0})",state)).isTrue();
        assertThat(eval("#vars.n between {0, 9007199254740992.0}",state)).isTrue();
        fails("0 between {1, #vars.n}",Map.of(),UNINITIALIZED);
    }
    @Test void astSizeAndDepthBoundariesAreIndependentOfRuntimeShortCircuiting() {
        String balanced=balancedComparisons(64); // 64 comparisons plus 63 ANDs: 255 parsed nodes.
        assertThat(eval("!"+balanced,Map.of())).isFalse(); // 256 nodes, including the root NOT.
        assertThatThrownBy(()->evaluator.validate(balanced+" and true",fields))
                .isInstanceOfSatisfying(ExpressionException.class,e->assertThat(e.kind()).isEqualTo(LIMIT)); // 257 nodes.
        assertThat(eval("!".repeat(31)+"true",Map.of())).isFalse(); // root depth is 1; leaf depth is 32.
        assertThatThrownBy(()->evaluator.validate("!".repeat(32)+"true",fields))
                .isInstanceOfSatisfying(ExpressionException.class,e->assertThat(e.kind()).isEqualTo(LIMIT));
    }
    @Test void sourceCountsCodePointsAndRejectsMalformedUnicode() {
        String source="'"+"😀".repeat(1992)+"' != ''";
        assertThat(source.codePointCount(0,source.length())).isEqualTo(2000);
        assertThat(eval(source,Map.of())).isTrue();
        assertThatThrownBy(()->evaluator.validate(source+" ",fields))
                .isInstanceOfSatisfying(ExpressionException.class,e->assertThat(e.kind()).isEqualTo(LIMIT));
        assertThatThrownBy(()->evaluator.validate("'\uD800' == ''",fields))
                .isInstanceOfSatisfying(ExpressionException.class,e->assertThat(e.kind()).isEqualTo(INVALID));
    }
    @Test void decimalPowerAllocationIsBoundedWithoutCheckingAccuracy() {
        var state=Map.of("d",value(VariableValueType.DECIMAL,"1000000000000000000000000000"));
        assertThat(eval("#vars.d ^ 9 > 0",state)).isTrue();
        fails("#vars.d ^ 10 > 0",state,LIMIT);
    }
    private static String balancedComparisons(int leaves) {
        if(leaves==1) return "true == true";
        return "("+balancedComparisons(leaves/2)+" and "+balancedComparisons(leaves-leaves/2)+")";
    }
}
