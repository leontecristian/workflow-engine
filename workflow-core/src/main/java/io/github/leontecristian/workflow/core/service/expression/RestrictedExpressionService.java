package io.github.leontecristian.workflow.core.service.expression;

import io.github.leontecristian.workflow.core.dto.expression.ExpressionEvaluation;
import io.github.leontecristian.workflow.core.dto.expression.ExpressionRead;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.ScalarValue;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.exception.expression.ExpressionException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.ast.Elvis;
import org.springframework.expression.spel.ast.OpAnd;
import org.springframework.expression.spel.ast.OpDivide;
import org.springframework.expression.spel.ast.OpEQ;
import org.springframework.expression.spel.ast.OpGE;
import org.springframework.expression.spel.ast.OpGT;
import org.springframework.expression.spel.ast.OpLE;
import org.springframework.expression.spel.ast.OpLT;
import org.springframework.expression.spel.ast.OpMinus;
import org.springframework.expression.spel.ast.OpModulus;
import org.springframework.expression.spel.ast.OpMultiply;
import org.springframework.expression.spel.ast.OpNE;
import org.springframework.expression.spel.ast.OpOr;
import org.springframework.expression.spel.ast.OpPlus;
import org.springframework.expression.spel.ast.OperatorBetween;
import org.springframework.expression.spel.ast.OperatorNot;
import org.springframework.expression.spel.ast.OperatorPower;
import org.springframework.expression.spel.ast.SpelNodeImpl;
import org.springframework.expression.spel.ast.Ternary;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import static io.github.leontecristian.workflow.core.exception.expression.ExpressionException.Kind.EVALUATION;
import static io.github.leontecristian.workflow.core.exception.expression.ExpressionException.Kind.INVALID;
import static io.github.leontecristian.workflow.core.exception.expression.ExpressionException.Kind.LIMIT;
import static io.github.leontecristian.workflow.core.exception.expression.ExpressionException.Kind.UNINITIALIZED;
import static io.github.leontecristian.workflow.core.exception.expression.ExpressionException.Kind.UNSUPPORTED;

/** Workflow-language v1 evaluator. Sources must come from the pinned definition, never a command. */
@Service
public class RestrictedExpressionService {
    public static final String PROFILE = "wf-restricted-spel-v1";
    public static final String SPRING_VERSION = org.springframework.expression.Expression.class.getPackage().getImplementationVersion();
    private record Key(String hash, String transition, String source, String implementation, Map<String, ActionField> declarations) {
        Key { declarations = Map.copyOf(declarations); }
    }
    private final Map<Key, ExpressionPlans.Plan> plans = new LinkedHashMap<>(128, 0.75f, true);

    /** Pure publication check of all source and declarations, without evaluating any live data. */
    public void validate(String source, Map<String, ActionField> declarations) { requireCompatible(); ExpressionPlans.compile(source, declarations); }
    /** Compatibility is fixed by this implementation, never supplied by a workflow or host setting. */
    public void requireCompatible() { requireVersion(SPRING_VERSION); }
    static void requireVersion(String version) { if(!"6.2.6".equals(version)) throw new ExpressionException(UNSUPPORTED); }

    /** The allowance belongs to the enclosing command; scalar values are detached immutable records. */
    public ExpressionEvaluation evaluate(String hash, String transition, String source, Map<String, ActionField> declarations,
            Map<String, ScalarValue> initialized, ExpressionAllowance allowance) {
        allowance.take();
        requireCompatible();
        Key key = new Key(hash, transition, source, PROFILE + ":" + SPRING_VERSION, declarations);
        ExpressionPlans.Plan plan;
        synchronized (plans) { plan = plans.get(key); }
        if (plan == null) {
            plan = ExpressionPlans.compile(source, declarations);
            synchronized (plans) {
                plans.put(key, plan);
                if (plans.size() > 128) plans.remove(plans.keySet().iterator().next());
            }
        }
        try {
            Evaluation state = new Evaluation(Map.copyOf(initialized));
            Object value = new Node(plan).getValue(state);
            if (!(value instanceof Boolean result)) throw new ExpressionException(EVALUATION);
            return new ExpressionEvaluation(result, new ArrayList<>(state.reads.values()));
        } catch (ExpressionException failure) { throw failure; }
        catch (RuntimeException failure) { throw new ExpressionException(EVALUATION); }
    }
    int cachedPlans() { synchronized (plans) { return plans.size(); } }

    private static final class Evaluation extends ExpressionState {
        private long remaining = 10_000;
        private final Map<String, ScalarValue> initialized;
        private final Map<String, ExpressionRead> reads = new LinkedHashMap<>();
        private boolean between;
        Evaluation(Map<String, ScalarValue> initialized) {
            super(context(), new SpelParserConfiguration(SpelCompilerMode.OFF, null, false, false, 0, 4000)); this.initialized = initialized;
            var nativeComparator=new org.springframework.expression.spel.support.StandardTypeComparator();
            ((StandardEvaluationContext)getEvaluationContext()).setTypeComparator(new org.springframework.expression.TypeComparator() {
                @Override public boolean canCompare(Object a,Object b) { return nativeComparator.canCompare(a,b); }
                @Override public int compare(Object a,Object b) {
                    if(a instanceof Number left && b instanceof Number right) NumericWork.check("Compare",left,right,Evaluation.this::work);
                    else if(between) stringWork(Evaluation.this,a,b);
                    return nativeComparator.compare(a,b);
                }
            });
        }
        void work(long amount) {
            if (amount < 0 || amount > remaining) throw new ExpressionException(LIMIT);
            remaining -= amount;
        }
        Object read(String key, ActionField declaration) {
            ScalarValue value = initialized.get(key);
            if (value == null) throw new ExpressionException(UNINITIALIZED);
            if (value.type()!=declaration.type() || (!declaration.nullable() && value.value()==io.github.leontecristian.workflow.core.dto.runtime.NullLiteral.INSTANCE)) throw new ExpressionException(EVALUATION);
            reads.putIfAbsent(key, new ExpressionRead(key, value));
            if (value.value() == io.github.leontecristian.workflow.core.dto.runtime.NullLiteral.INSTANCE) return null;
            if (value.value() instanceof io.github.leontecristian.workflow.core.dto.runtime.BooleanLiteral b) return b.value();
            String text = ((TextLiteral)value.value()).value();
            return switch (value.type()) {
                case STRING -> text; case LONG -> Long.valueOf(text); case DECIMAL -> new BigDecimal(text);
                case DATE -> LocalDate.parse(text); case DATETIME -> Instant.parse(text);
                default -> throw new ExpressionException(EVALUATION);
            };
        }
    }
    private static StandardEvaluationContext context() {
        var context = new StandardEvaluationContext();
        context.setPropertyAccessors(List.of()); context.setIndexAccessors(List.of());
        context.setMethodResolvers(List.of()); context.setConstructorResolvers(List.of());
        context.setTypeLocator(name -> { throw new ExpressionException(EVALUATION); });
        context.setTypeConverter(new TypeConverter() {
            @Override public boolean canConvert(TypeDescriptor source, TypeDescriptor target) {
                return source != null && (target.getObjectType().isAssignableFrom(source.getObjectType()) || target.getObjectType() == String.class);
            }
            @Override public Object convertValue(Object value, TypeDescriptor source, TypeDescriptor target) {
                if (value != null && target.getObjectType().isInstance(value)) return value;
                if (target.getObjectType() == String.class) return String.valueOf(value);
                throw new ExpressionException(EVALUATION);
            }
        });
        return context;
    }
    private static final class Constant extends SpelNodeImpl {
        private final Object value;
        Constant(Object value) { super(0, 1); this.value = value; }
        @Override public TypedValue getValueInternal(ExpressionState state) { return new TypedValue(value); }
        @Override public String toStringAST() { return "<scalar>"; }
    }
    private static final class Node extends SpelNodeImpl {
        private final ExpressionPlans.Plan plan;
        Node(ExpressionPlans.Plan plan) {
            super(0, 1, plan.children().stream().map(Node::new).toArray(SpelNodeImpl[]::new)); this.plan = plan;
        }
        @Override public TypedValue getValueInternal(ExpressionState expressionState) {
            Evaluation state = (Evaluation)expressionState; state.work(plan.cost());
            String kind = plan.kind();
            if (kind.equals("Read")) return new TypedValue(state.read(plan.text(),(ActionField)plan.literal()));
            if (kind.equals("Literal")) {
                return new TypedValue(plan.literal());
            }
            if (List.of("OpAnd", "OpOr", "OperatorNot", "Ternary", "Elvis").contains(kind))
                return nativeNode(kind, children).getValueInternal(state);
            Object[] values = new Object[children.length];
            for (int i = 0; i < children.length; i++) values[i] = children[i].getValueInternal(state).getValue();
            if (kind.equals("InlineList")) return new TypedValue(java.util.Arrays.asList(values));
            boolean numeric = values[0] instanceof Number && (values.length == 1 || values[1] instanceof Number);
            if (numeric) NumericWork.check(isComparison(kind) ? "Compare" : kind,
                    (Number)values[0], values.length == 1 ? null : (Number)values[1], state::work);
            if (!kind.equals("OperatorBetween")) stringWork(state, values[0], values.length > 1 ? values[1] : null);
            if (kind.equals("OpMultiply") && values[0] instanceof String text && values[1] instanceof Integer count) {
                long size = text.length() * (long)count;
                if (size > 256) throw new ExpressionException(LIMIT);
                state.work(Math.max(1, Math.max(0, size)));
            }
            if (kind.equals("OpPlus") && values.length == 2 && (values[0] instanceof String || values[1] instanceof String)) {
                long size = String.valueOf(values[0]).length() + (long)String.valueOf(values[1]).length();
                if (size > 8192) throw new ExpressionException(LIMIT);
                state.work(Math.max(1, size));
            }
            var constants = java.util.Arrays.stream(values).map(Constant::new).toArray(SpelNodeImpl[]::new);
            TypedValue result;
            boolean previousBetween=state.between;
            try {
                state.between=kind.equals("OperatorBetween");
                result=nativeNode(kind, constants).getValueInternal(state);
            } finally { state.between=previousBetween; }
            if (result.getValue() instanceof Number number) NumericWork.capacity(number);
            if (result.getValue() instanceof String s) {
                if (s.length() > 8192) throw new ExpressionException(LIMIT);
            }
            return result;
        }
        @Override public String toStringAST() { return "<validated>"; }
    }
    private static void stringWork(Evaluation state, Object a, Object b) {
        if (a instanceof String s) state.work(Math.max(1, s.length()));
        if (b instanceof String s) state.work(Math.max(1, s.length()));
    }
    private static boolean isComparison(String kind) { return List.of("OpEQ", "OpNE", "OpLT", "OpLE", "OpGT", "OpGE").contains(kind); }
    private static SpelNodeImpl nativeNode(String kind, SpelNodeImpl[] c) {
        return switch (kind) {
            case "OpAnd" -> new OpAnd(0, 1, c); case "OpOr" -> new OpOr(0, 1, c);
            case "OperatorNot" -> new OperatorNot(0, 1, c[0]); case "OpEQ" -> new OpEQ(0, 1, c);
            case "OpNE" -> new OpNE(0, 1, c); case "OpLT" -> new OpLT(0, 1, c);
            case "OpLE" -> new OpLE(0, 1, c); case "OpGT" -> new OpGT(0, 1, c);
            case "OpGE" -> new OpGE(0, 1, c); case "OpPlus" -> new OpPlus(0, 1, c);
            case "OpMinus" -> new OpMinus(0, 1, c); case "OpMultiply" -> new OpMultiply(0, 1, c);
            case "OpDivide" -> new OpDivide(0, 1, c); case "OpModulus" -> new OpModulus(0, 1, c);
            case "OperatorPower" -> new OperatorPower(0, 1, c); case "Ternary" -> new Ternary(0, 1, c[0], c[1], c[2]);
            case "Elvis" -> new Elvis(0, 1, c); case "OperatorBetween" -> new OperatorBetween(0, 1, c);
            default -> throw new ExpressionException(INVALID);
        };
    }
}
