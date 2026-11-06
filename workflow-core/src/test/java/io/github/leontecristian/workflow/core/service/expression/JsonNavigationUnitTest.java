package io.github.leontecristian.workflow.core.service.expression;

import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.expression.ExpressionEvaluation;
import io.github.leontecristian.workflow.core.exception.expression.ExpressionException;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JsonNavigationUnitTest {
    private final RestrictedExpressionService service=new RestrictedExpressionService();
    private final Map<String,ActionField> declarations=Map.of("booking",new ActionField("booking",VariableValueType.JSON,false,true),
            "other",new ActionField("other",VariableValueType.JSON,false,true));
    private ExpressionEvaluation evaluate(String expression,String booking,String other) {
        return service.evaluate("json-test",expression,expression,declarations,Map.of("booking",new WorkflowValue(VariableValueType.JSON,WorkflowJsonValue.parse(booking)),
                "other",new WorkflowValue(VariableValueType.JSON,WorkflowJsonValue.parse(other))),new ExpressionAllowance());
    }
    @Test void standardParserSupportsReadOnlyPropertyIndexAndComputedRootEvaluation() {
        var result=evaluate("#vars.booking.reviewers[#vars.other.index]?.name == 'Ana' and #vars.booking[#vars.other.key] == 1.00",
                "{\"reviewers\":[{\"name\":\"Ana\"}],\"amount\":1.00}","{\"index\":0.00,\"key\":\"amount\"}");
        assertThat(result.result()).isTrue();
        assertThat(result.reads()).extracting(r->r.variableKey()).containsExactly("other","booking","other","booking");
        assertThat(result.reads().get(1).path()).extracting(p->p.kind()).containsExactly("PROPERTY","INDEX","PROPERTY");
        assertThat(result.reads().get(3).valueJson()).isEqualTo("1.00");
    }
    @Test void missingNullSafeAndShortCircuitDoNotReadSkippedComputedOperands() {
        var result=evaluate("#vars.booking.absent?.[#vars.other.uninitialized] == null and (true or #vars.other.skipped == null)","{}","{}");
        assertThat(result.result()).isTrue();
        assertThat(result.reads()).extracting(r->r.state()).containsExactly("MISSING","NULL_SAFE");
        assertThat(result.reads()).allSatisfy(r->assertThat(r.path()).hasSize(1));
        assertThat(evaluate("#vars.booking.a[-1]?.name == null and #vars.booking.a[99] == null","{\"a\":[]}","{}").result()).isTrue();
        assertThatThrownBy(()->evaluate("#vars.booking.absent.code == null","{}","{}")).isInstanceOf(ExpressionException.class);
        assertThatThrownBy(()->evaluate("#vars.booking.absent?.name.code == null","{}","{}")).isInstanceOf(ExpressionException.class);
    }
    @Test void invalidIndexesAndScalarContainerOperandsFail() {
        for(String index:List.of("-1.5","1.5","'0'","true","null"))
            assertThatThrownBy(()->evaluate("#vars.booking.a["+index+"] == null","{\"a\":[]}","{}")).isInstanceOf(ExpressionException.class);
        for(String expression:List.of("#vars.booking == 1","#vars.booking + 1 == 1","#vars.booking.a.x == null","#vars.booking[0] == null"))
            assertThatThrownBy(()->evaluate(expression,"{\"a\":1}","{}")).isInstanceOf(ExpressionException.class);
    }
    @Test void exactKeysAreDataAndHostCapabilitiesStayUnavailable() {
        assertThat(evaluate("#vars.booking['a.b'] == 'x' and #vars.booking.class == 'data' and #vars.booking['it''s'] == true",
                "{\"a.b\":\"x\",\"class\":\"data\",\"it's\":true}","{}").result()).isTrue();
        for(String expression:List.of("#vars.booking.getClass() == null","T(java.lang.Runtime).getRuntime() == null","@bean == null",
                "#vars.booking.x = 1","#vars.booking.?[true] == null","#vars.booking.![true] == null","new java.lang.String() == ''"))
            assertThatThrownBy(()->service.validate(expression,declarations)).isInstanceOf(ExpressionException.class);
    }
    @Test void containerEqualityIsBoundedScaleSensitiveAndIndependentOfWrapperIdentity() {
        assertThat(evaluate("#vars.booking == #vars.other","{\"a\":[1.00],\"b\":null}","{\"b\":null,\"a\":[1.00]}").result()).isTrue();
        assertThat(evaluate("#vars.booking == #vars.other","{\"a\":[1.00]}","{\"a\":[1]}").result()).isFalse();
        assertThat(evaluate("#vars.booking.a[0] == #vars.other.a[0]","{\"a\":[1.00]}","{\"a\":[1]}").result()).isTrue();
        String large="[\""+"x".repeat(16000)+"\"]";
        assertThatThrownBy(()->evaluate("#vars.booking == #vars.other",large,large)).isInstanceOfSatisfying(ExpressionException.class,e->assertThat(e.kind()).isEqualTo(ExpressionException.Kind.LIMIT));
    }
}
