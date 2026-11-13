package io.github.leontecristian.workflow.core.service.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Binding;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.BooleanLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.NullLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.runtime.StartInputs;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure preparation. A mapping never observes another mapping's result. */
final class RuntimeValueBindings {
    private RuntimeValueBindings() {}
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));
    private static JsonNode jsonToken(String source) {
        try { return JSON.readTree(source); }
        catch (java.io.IOException failure) { throw new WorkflowExecutionException(Category.HOST_CONFIGURATION); }
    }
    static JsonNode token(WorkflowLiteral value) {
        var f=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
        if (value==NullLiteral.INSTANCE) return f.nullNode();
        if (value instanceof io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue j) return jsonToken(j.toJson());
        if (value instanceof TextLiteral t) return f.textNode(t.value());
        return f.booleanNode(((BooleanLiteral)value).value());
    }
    static ObjectNode tokens(Map<String,WorkflowValue> values,RuntimeJson json) {
        var envelope=new TokenEnvelope(json,RuntimeJson.MAP_UTF16_BYTES / 2);
        values.forEach((k,v)->envelope.add(k,v.value())); return envelope.values;
    }
    private static final class TokenEnvelope {
        private final RuntimeJson json;
        private final int maxChars;
        private final ObjectNode values;
        private long chars=2, bytes=2;
        TokenEnvelope(RuntimeJson json,int maxChars) { this.json=json; this.maxChars=maxChars; values=json.object(); }
        void add(String key,WorkflowLiteral literal) {
            JsonNode scalar=literal instanceof io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue ? null : token(literal);
            String encoded=literal instanceof io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue value ? value.toJson() : json.encode(scalar);
            long punctuation=key.length()+3L+(values.isEmpty() ? 0 : 1);
            chars+=punctuation+encoded.length(); bytes+=punctuation+encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (chars > maxChars || bytes > RuntimeJson.MAP_UTF8_BYTES)
                throw WorkflowExecutionException.resultLimit("The complete execution value map exceeds its capacity.");
            values.set(key,scalar==null ? token(literal) : scalar);
        }
    }
    static void basic(StartInputs inputs,RuntimeJson json) {
        RuntimeInputs.require(inputs!=null && inputs.values().size()<=100,Category.INVALID_INPUT);
        var raw=new TokenEnvelope(json,RuntimeJson.MAP_UTF16_BYTES / 2);
        for (var e:inputs.values().entrySet()) {
            RuntimeInputs.require(RuntimeInputs.key(e.getKey()),Category.INVALID_INPUT);
            if (e.getValue() instanceof TextLiteral t) RuntimeInputs.require(RuntimeInputs.unicode(t.value()) && t.value().length()<=4000,Category.INVALID_INPUT);
            raw.add(e.getKey(),e.getValue());
        }
    }
    static Map<String,WorkflowValue> inputs(MinimalWorkflowDefinition model,StartInputs supplied,RuntimeJson json) {
        RuntimeInputs.require(model.inputs().keySet().containsAll(supplied.values().keySet()),Category.INVALID_INPUT);
        Map<String,WorkflowValue> result=new TreeMap<>();
        model.inputs().forEach((key,decl) -> {
            WorkflowLiteral token=supplied.values().get(key);
            if (token==null) token=decl.defaultValue();
            RuntimeInputs.require(token!=null || !decl.field().required(),Category.INVALID_INPUT);
            if (token!=null) {
                try { var v=new WorkflowValue(decl.field().type(),token); validate(decl.field(),v,Category.INVALID_INPUT); result.put(key,v); }
                catch (IllegalArgumentException | java.time.DateTimeException e) { throw new WorkflowExecutionException(Category.INVALID_INPUT,e); }
            }
        });
        envelope(json.encode(tokens(result,json)));
        return Map.copyOf(result);
    }
    static Map<String,WorkflowValue> prepare(Map<String,Binding> bindings,Map<String,ActionField> declarations,
            Map<String,WorkflowValue> before,Map<String,WorkflowValue> sources,Category category) {
        Map<String,WorkflowValue> writes=new TreeMap<>();
        for (var e:bindings.entrySet()) {
            var b=e.getValue(); var dest=declarations.get(e.getKey());
            RuntimeInputs.require(dest!=null,Category.HOST_CONFIGURATION);
            WorkflowValue value;
            if (b.source().equals("literal")) value=new WorkflowValue(dest.type(),b.value());
            else if (b.source().equals("variable")) {
                value=before.get(b.key()); RuntimeInputs.require(value!=null,Category.UNINITIALIZED_VARIABLE);
            } else value=sources.get(b.key());
            if (value!=null) { validate(dest,value,category); writes.put(e.getKey(),value); }
        }
        return writes;
    }
    static void validate(ActionField field,WorkflowValue value,Category category) {
        RuntimeInputs.require(value!=null && value.type()==field.type() && (field.nullable() || value.value()!=NullLiteral.INSTANCE),category);
    }
    static void output(List<ActionField> fields,Map<String,WorkflowValue> values,RuntimeJson json) {
        RuntimeInputs.require(values!=null && values.size()<=100,Category.INVALID_ACTION_OUTPUT);
        var known=new java.util.HashSet<String>();
        for (var f:fields) {
            known.add(f.key()); var v=values.get(f.key());
            RuntimeInputs.require(v!=null || !f.required(),Category.INVALID_ACTION_OUTPUT);
            if (v!=null) validate(f,v,Category.INVALID_ACTION_OUTPUT);
        }
        RuntimeInputs.require(known.containsAll(values.keySet()),Category.INVALID_ACTION_OUTPUT);
        envelope(json.encode(tokens(values,json)));
    }
    static void envelope(String value) {
        if (value.length()*2L > RuntimeJson.MAP_UTF16_BYTES || !RuntimeJson.utf8Fits(value,RuntimeJson.MAP_UTF8_BYTES))
            throw WorkflowExecutionException.resultLimit("The complete execution value map exceeds its capacity.");
    }
    static ObjectNode source(Binding b,RuntimeJson json) {
        var n=json.object(); n.put("source",b.source());
        if (b.source().equals("activitySubmissions")) { n.put("nodeKey",b.key()); return n; }
        if (b.source().equals("literal")) n.set("value",token(b.value())); else n.put("key",b.key()); return n;
    }
}
