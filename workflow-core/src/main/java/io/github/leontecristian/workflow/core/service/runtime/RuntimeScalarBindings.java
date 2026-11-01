package io.github.leontecristian.workflow.core.service.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Binding;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.BooleanLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.NullLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.ScalarLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.ScalarValue;
import io.github.leontecristian.workflow.core.dto.runtime.StartInputs;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure preparation. A mapping never observes another mapping's result. */
final class RuntimeScalarBindings {
    private RuntimeScalarBindings() {}
    static JsonNode token(ScalarLiteral value) {
        var f=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
        if (value==NullLiteral.INSTANCE) return f.nullNode();
        if (value instanceof TextLiteral t) return f.textNode(t.value());
        return f.booleanNode(((BooleanLiteral)value).value());
    }
    static ObjectNode tokens(Map<String,ScalarValue> values,RuntimeJson json) {
        var result=json.object(); values.forEach((k,v) -> result.set(k,token(v.value()))); return result;
    }
    static void basic(StartInputs inputs,RuntimeJson json) {
        RuntimeInputs.require(inputs!=null && inputs.values().size()<=100,Category.INVALID_INPUT);
        var raw=json.object();
        for (var e:inputs.values().entrySet()) {
            RuntimeInputs.require(RuntimeInputs.key(e.getKey()),Category.INVALID_INPUT);
            if (e.getValue() instanceof TextLiteral t) RuntimeInputs.require(RuntimeInputs.unicode(t.value()) && t.value().length()<=4000,Category.INVALID_INPUT);
            raw.set(e.getKey(),token(e.getValue()));
        }
        RuntimeInputs.require(json.encode(raw).length()*2L<=128*1024,Category.LIMIT_EXCEEDED);
    }
    static Map<String,ScalarValue> inputs(MinimalWorkflowDefinition model,StartInputs supplied,RuntimeJson json) {
        RuntimeInputs.require(model.inputs().keySet().containsAll(supplied.values().keySet()),Category.INVALID_INPUT);
        Map<String,ScalarValue> result=new TreeMap<>();
        model.inputs().forEach((key,decl) -> {
            ScalarLiteral token=supplied.values().get(key);
            if (token==null) token=decl.defaultValue();
            RuntimeInputs.require(token!=null || !decl.field().required(),Category.INVALID_INPUT);
            if (token!=null) {
                try { var v=new ScalarValue(decl.field().type(),token); validate(decl.field(),v,Category.INVALID_INPUT); result.put(key,v); }
                catch (IllegalArgumentException | java.time.DateTimeException e) { throw new WorkflowExecutionException(Category.INVALID_INPUT,e); }
            }
        });
        RuntimeInputs.require(json.encode(tokens(result,json)).length()*2L<=128*1024,Category.LIMIT_EXCEEDED);
        return Map.copyOf(result);
    }
    static Map<String,ScalarValue> prepare(Map<String,Binding> bindings,Map<String,ActionField> declarations,
            Map<String,ScalarValue> before,Map<String,ScalarValue> sources,Category category) {
        Map<String,ScalarValue> writes=new TreeMap<>();
        for (var e:bindings.entrySet()) {
            var b=e.getValue(); var dest=declarations.get(e.getKey());
            RuntimeInputs.require(dest!=null,Category.HOST_CONFIGURATION);
            ScalarValue value;
            if (b.source().equals("literal")) value=new ScalarValue(dest.type(),b.value());
            else if (b.source().equals("variable")) {
                value=before.get(b.key()); RuntimeInputs.require(value!=null,Category.UNINITIALIZED_VARIABLE);
            } else value=sources.get(b.key());
            if (value!=null) { validate(dest,value,category); writes.put(e.getKey(),value); }
        }
        return writes;
    }
    static void validate(ActionField field,ScalarValue value,Category category) {
        RuntimeInputs.require(value!=null && value.type()==field.type() && (field.nullable() || value.value()!=NullLiteral.INSTANCE),category);
    }
    static void output(List<ActionField> fields,Map<String,ScalarValue> values,RuntimeJson json) {
        RuntimeInputs.require(values!=null && values.size()<=100,Category.INVALID_ACTION_OUTPUT);
        var known=new java.util.HashSet<String>();
        for (var f:fields) {
            known.add(f.key()); var v=values.get(f.key());
            RuntimeInputs.require(v!=null || !f.required(),Category.INVALID_ACTION_OUTPUT);
            if (v!=null) validate(f,v,Category.INVALID_ACTION_OUTPUT);
        }
        RuntimeInputs.require(known.containsAll(values.keySet()),Category.INVALID_ACTION_OUTPUT);
        RuntimeInputs.require(RuntimeJson.fits(json.encode(tokens(values,json)),64*1024),Category.LIMIT_EXCEEDED);
    }
    static ObjectNode source(Binding b,RuntimeJson json) {
        var n=json.object(); n.put("source",b.source());
        if (b.source().equals("literal")) n.set("value",token(b.value())); else n.put("key",b.key()); return n;
    }
}
