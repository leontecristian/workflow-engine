package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.leontecristian.workflow.core.dto.definition.DiagnosticStage;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Binding;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.BooleanLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.NullLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import java.util.HashMap;
import java.util.Map;

/** Pure semantic checks after the bounded structural schema. */
final class ExecutionValueDefinitionModel {
    private ExecutionValueDefinitionModel() {}
    static WorkflowLiteral literal(JsonNode value) {
        if (value.isContainerNode()) return io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue.parse(DefinitionContentCanonicalizer.canonical(value));
        if (value.isNull()) return NullLiteral.INSTANCE;
        if (value.isBoolean()) return new BooleanLiteral(value.booleanValue());
        if (value.isTextual()) return new TextLiteral(value.textValue());
        throw new IllegalArgumentException("Invalid scalar token");
    }
    private static ActionField field(JsonNode n, boolean input) {
        return new ActionField(n.path("key").textValue(), VariableValueType.valueOf(n.path("type").textValue()),
                input && n.path("required").booleanValue(), n.path("nullable").booleanValue());
    }
    private static Map<String,Binding> bindings(JsonNode n) {
        Map<String,Binding> result = new HashMap<>();
        n.fields().forEachRemaining(e -> result.put(e.getKey(), new Binding(e.getValue().path("source").textValue(),
                e.getValue().path("key").textValue(), e.getValue().has("value") ? literal(e.getValue().get("value")) : null)));
        return result;
    }
    static MinimalWorkflowDefinition load(JsonNode root, DefinitionDiagnostics d) {
        return load(root,d,null);
    }
    static MinimalWorkflowDefinition load(JsonNode root, DefinitionDiagnostics d,
            io.github.leontecristian.workflow.core.service.expression.RestrictedExpressionService expressions) {
        return load(root, d, expressions, true);
    }
    static MinimalWorkflowDefinition load(JsonNode root, DefinitionDiagnostics d,
            io.github.leontecristian.workflow.core.service.expression.RestrictedExpressionService expressions,
            boolean authoring) {
        Map<String,MinimalWorkflowDefinition.Input> inputs = new HashMap<>();
        Map<String,ActionField> variables = new HashMap<>();
        int i=0;
        for (var n : root.path("startPolicy").path("inputContract")) {
            String path="/startPolicy/inputContract/"+i++;
            try {
                var f=field(n,true);
                WorkflowLiteral def=n.has("default") ? literal(n.get("default")) : null;
                if (f.required() && def!=null) throw new IllegalArgumentException();
                if (def!=null) validate(f,new WorkflowValue(f.type(),def));
                if (inputs.putIfAbsent(f.key(),new MinimalWorkflowDefinition.Input(f,def))!=null) throw new IllegalArgumentException();
            } catch (RuntimeException e) { error(d,"INVALID_INPUT_CONTRACT",path); }
        }
        i=0;
        for (var n : root.path("variables")) {
            try { var f=field(n,false); if (variables.putIfAbsent(f.key(),f)!=null) throw new IllegalArgumentException(); }
            catch (RuntimeException e) { error(d,"INVALID_VARIABLE","/variables/"+i); }
            i++;
        }
        var initialize=bindings(root.path("startPolicy").path("initialize"));
        for (var e:initialize.entrySet()) {
            try {
                var dest=variables.get(e.getKey()); var source=e.getValue();
                if (dest==null) throw new IllegalArgumentException();
                if (source.source().equals("literal")) validate(dest,new WorkflowValue(dest.type(),source.value()));
                else compatible(inputs.get(source.key()).field(),dest);
            } catch (RuntimeException failure) { error(d,"INVALID_BINDING","/startPolicy/initialize/"+e.getKey()); }
        }
        Map<String,MinimalWorkflowDefinition.Service> services=new HashMap<>();
        int nodeIndex=0;
        for (var n:root.path("nodes")) {
            String key=n.path("key").textValue();
            if ("SERVICE_TASK".equals(n.path("type").textValue()))
                services.put(key,new MinimalWorkflowDefinition.Service(key,n.path("action").textValue(),bindings(n.path("inputs")),bindings(n.path("outputs")),nodeIndex));
            nodeIndex++;
        }
        return RoutingDefinitionModel.load(root,d,inputs,variables,initialize,services,expressions,authoring);
    }
    static void validate(ActionField field, WorkflowValue value) {
        if (value.type()!=field.type() || (!field.nullable() && value.value()==NullLiteral.INSTANCE)) throw new IllegalArgumentException();
    }
    static void compatible(ActionField source, ActionField target) {
        if (source==null || target==null || source.type()!=target.type() || (source.nullable() && !target.nullable())) throw new IllegalArgumentException();
    }
    static void error(DefinitionDiagnostics d,String code,String path) {
        d.error(code,DiagnosticStage.CONTRACT,"Invalid scalar/service definition contract.",path);
    }
}
