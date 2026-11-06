package io.github.leontecristian.workflow.core.service.expression;

import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.exception.expression.ExpressionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.ast.BooleanLiteral;
import org.springframework.expression.spel.ast.CompoundExpression;
import org.springframework.expression.spel.ast.Elvis;
import org.springframework.expression.spel.ast.Indexer;
import org.springframework.expression.spel.ast.InlineList;
import org.springframework.expression.spel.ast.IntLiteral;
import org.springframework.expression.spel.ast.Literal;
import org.springframework.expression.spel.ast.LongLiteral;
import org.springframework.expression.spel.ast.NullLiteral;
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
import org.springframework.expression.spel.ast.PropertyOrFieldReference;
import org.springframework.expression.spel.ast.RealLiteral;
import org.springframework.expression.spel.ast.StringLiteral;
import org.springframework.expression.spel.ast.Ternary;
import org.springframework.expression.spel.ast.VariableReference;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import static io.github.leontecristian.workflow.core.exception.expression.ExpressionException.Kind.INVALID;
import static io.github.leontecristian.workflow.core.exception.expression.ExpressionException.Kind.LIMIT;

/** Pure compilation into immutable descriptions. No Spring AST survives compilation. */
final class ExpressionPlans {
    enum Type { BOOLEAN, NUMBER, STRING, DATE, INSTANT, NULL, MIXED, LIST }
    record Plan(String kind, Object literal, String text, List<Plan> children, Type type, int cost) {
        Plan { children = List.copyOf(children); }
    }
    private static final List<Class<?>> ALLOWED = List.of(BooleanLiteral.class, NullLiteral.class, StringLiteral.class,
            IntLiteral.class, LongLiteral.class, RealLiteral.class, OpAnd.class, OpOr.class, OperatorNot.class,
            OpEQ.class, OpNE.class, OpLT.class, OpLE.class, OpGT.class, OpGE.class, OpPlus.class, OpMinus.class,
            OpMultiply.class, OpDivide.class, OpModulus.class, OperatorPower.class, Ternary.class, Elvis.class,
            OperatorBetween.class, InlineList.class);
    static Plan compile(String source, Map<String, ActionField> declarations) {
        preflight(source);
        try {
            var config = new SpelParserConfiguration(SpelCompilerMode.OFF, null, false, false, 0, 4000);
            var parsed = (SpelExpression) new SpelExpressionParser(config).parseExpression(source);
            size(parsed.getAST(), 1, new int[1]);
            Plan result = describe(parsed.getAST(), source, declarations, false);
            if (result.type() != Type.BOOLEAN && result.type() != Type.MIXED) throw new ExpressionException(INVALID);
            return result;
        } catch (ExpressionException failure) { throw failure; }
        catch (RuntimeException | StackOverflowError failure) { throw new ExpressionException(INVALID); }
    }
    static void preflight(String s) {
        if (s == null || s.isBlank()) throw new ExpressionException(INVALID);
        if (s.codePointCount(0, s.length()) > 2000) throw new ExpressionException(LIMIT);
        int nesting = 0, descent = 0; char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) throw new ExpressionException(INVALID);
                i++; continue;
            }
            if (Character.isLowSurrogate(c)) throw new ExpressionException(INVALID);
            if (quote != 0) {
                if (c == quote) {
                    if (i + 1 < s.length() && s.charAt(i + 1) == quote) i++; else quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') { quote = c; continue; }
            if (c == '(' || c == '[' || c == '{') { nesting++; descent++; }
            if (c == ')' || c == ']' || c == '}') nesting--;
            if (c == '?' || c == '!' || c == '+' || c == '-') descent++;
            if (Character.isLetter(c) || c == '_' || c == '$') {
                int start = i;
                while (i + 1 < s.length() && Character.isJavaIdentifierPart(s.charAt(i + 1))) i++;
                if (s.substring(start, i + 1).equalsIgnoreCase("not")) descent++;
            }
            if (nesting > 32 || descent > 64) throw new ExpressionException(LIMIT);
        }
    }
    private static void size(SpelNode n, int depth, int[] count) {
        if (++count[0] > 256 || depth > 32) throw new ExpressionException(LIMIT);
        for (int i = 0; i < n.getChildCount(); i++) size(n.getChild(i), depth + 1, count);
    }
    private static Plan describe(SpelNode n, String source, Map<String, ActionField> declarations, boolean listAllowed) {
        if (n.getClass() == CompoundExpression.class) {
            if (n.getChildCount() < 2 || n.getChild(0).getClass() != VariableReference.class
                    || !n.getChild(0).toStringAST().equals("#vars")) throw new ExpressionException(INVALID);
            SpelNode access = n.getChild(1); String key; int cost = 3;
            if (access.getClass() == PropertyOrFieldReference.class) key = ((PropertyOrFieldReference) access).getName();
            else if (access.getClass() == Indexer.class && access.getChildCount() == 1
                    && access.getChild(0).getClass() == StringLiteral.class) {
                key = (String) ((StringLiteral) access.getChild(0)).getLiteralValue().getValue(); cost++;
            } else throw new ExpressionException(INVALID);
            ActionField field = declarations.get(key);
            if (field == null) throw new ExpressionException(INVALID);
            Type type = switch (field.type()) {
                case BOOLEAN -> Type.BOOLEAN; case LONG, DECIMAL -> Type.NUMBER;
                case STRING -> Type.STRING; case DATE -> Type.DATE; case DATETIME -> Type.INSTANT;
                case JSON -> Type.MIXED;
            };
            var steps = new ArrayList<Plan>();
            for (int i = 2; i < n.getChildCount(); i++) {
                if (field.type() != io.github.leontecristian.workflow.core.dto.runtime.VariableValueType.JSON) throw new ExpressionException(INVALID);
                SpelNode step = n.getChild(i);
                if (step.getClass() == PropertyOrFieldReference.class) {
                    var property = (PropertyOrFieldReference) step;
                    steps.add(new Plan("Property", property.isNullSafe(), property.getName(), List.of(), Type.MIXED, 1));
                } else if (step.getClass() == Indexer.class && step.getChildCount() == 1) {
                    steps.add(new Plan("Index", ((Indexer)step).isNullSafe(), null,
                            List.of(describe(step.getChild(0),source,declarations,false)),Type.MIXED,1));
                } else throw new ExpressionException(INVALID);
            }
            return new Plan("Read", field, key, steps, type, cost);
        }
        if (!ALLOWED.contains(n.getClass()) || (n.getClass() == InlineList.class && !listAllowed)) throw new ExpressionException(INVALID);
        if (n instanceof Literal literal) {
            Object value = literal.getLiteralValue().getValue();
            Type type = value == null ? Type.NULL : value instanceof Boolean ? Type.BOOLEAN : value instanceof String ? Type.STRING : Type.NUMBER;
            String text = source.substring(n.getStartPosition(), n.getEndPosition());
            return new Plan("Literal", value, text, List.of(), type, 1);
        }
        var children = new ArrayList<Plan>();
        if (n.getClass() == OperatorBetween.class && (n.getChild(1).getClass() != InlineList.class || n.getChild(1).getChildCount() != 2))
            throw new ExpressionException(INVALID);
        for (int i = 0; i < n.getChildCount(); i++) children.add(describe(n.getChild(i), source, declarations, n.getClass() == OperatorBetween.class && i == 1));
        String kind = n.getClass().getSimpleName();
        Type type = infer(kind, children);
        return new Plan(kind, null, null, children, type, 1);
    }
    private static Type infer(String kind, List<Plan> c) {
        Type a = c.get(0).type(), b = c.size() > 1 ? c.get(1).type() : null;
        if (kind.equals("OpAnd") || kind.equals("OpOr") || kind.equals("OperatorNot") || kind.equals("Ternary")) {
            bool(a);
            if (kind.equals("Ternary")) return join(b, c.get(2).type());
            if (b != null) bool(b);
            return Type.BOOLEAN;
        }
        if (kind.equals("Elvis")) return a == Type.NULL ? b : join(a, b);
        if (kind.equals("InlineList")) return Type.LIST;
        if (kind.equals("OperatorBetween")) {
            for(var bound:c.get(1).children()) comparable(a,bound.type());
            return Type.BOOLEAN;
        }
        if (kind.equals("OpEQ") || kind.equals("OpNE")) return Type.BOOLEAN;
        if (List.of("OpLT", "OpLE", "OpGT", "OpGE").contains(kind)) {
            comparable(a,b);
            return Type.BOOLEAN;
        }
        if (kind.equals("OpPlus") && b != null && (a == Type.STRING || b == Type.STRING)) return Type.STRING;
        if ((kind.equals("OpMultiply") || kind.equals("OpMinus")) && a == Type.STRING && b == Type.NUMBER) return Type.STRING;
        if (a != Type.NUMBER && a != Type.MIXED) throw new ExpressionException(INVALID);
        if (b != null && b != Type.NUMBER && b != Type.MIXED) throw new ExpressionException(INVALID);
        return Type.NUMBER;
    }
    private static void bool(Type type) { if (type != Type.BOOLEAN && type != Type.MIXED) throw new ExpressionException(INVALID); }
    private static void comparable(Type a,Type b) {
        if (a != b && a != Type.MIXED && b != Type.MIXED && a != Type.NULL && b != Type.NULL) throw new ExpressionException(INVALID);
    }
    private static Type join(Type a, Type b) { return a == b ? a : Type.MIXED; }
}
