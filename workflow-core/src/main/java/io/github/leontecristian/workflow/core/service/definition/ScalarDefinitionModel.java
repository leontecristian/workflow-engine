package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.leontecristian.workflow.core.dto.definition.DiagnosticStage;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Binding;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.BooleanLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.NullLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.ScalarLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.ScalarValue;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Pure semantic checks after the bounded structural schema. */
final class ScalarDefinitionModel {
    private ScalarDefinitionModel() {}
    static ScalarLiteral literal(JsonNode value) {
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
        Map<String,MinimalWorkflowDefinition.Input> inputs = new HashMap<>();
        Map<String,ActionField> variables = new HashMap<>();
        int i=0;
        for (var n : root.path("startPolicy").path("inputContract")) {
            String path="/startPolicy/inputContract/"+i++;
            try {
                var f=field(n,true);
                ScalarLiteral def=n.has("default") ? literal(n.get("default")) : null;
                if (f.required() && def!=null) throw new IllegalArgumentException();
                if (def!=null) validate(f,new ScalarValue(f.type(),def));
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
                if (source.source().equals("literal")) validate(dest,new ScalarValue(dest.type(),source.value()));
                else compatible(inputs.get(source.key()).field(),dest);
            } catch (RuntimeException failure) { error(d,"INVALID_BINDING","/startPolicy/initialize/"+e.getKey()); }
        }
        Map<String,JsonNode> nodes=new HashMap<>();
        Map<String,MinimalWorkflowDefinition.Service> services=new HashMap<>();
        JsonNode start=null,human=null,end=null;
        int starts=0,humans=0,ends=0;
        int nodeIndex=0;
        for (var n:root.path("nodes")) {
            String key=n.path("key").textValue();
            if (nodes.putIfAbsent(key,n)!=null) error(d,"DUPLICATE_KEY","/nodes");
            switch (n.path("type").textValue()) {
                case "START" -> { start=n; starts++; }
                case "HUMAN_TASK" -> { human=n; humans++; }
                case "END" -> { end=n; ends++; }
                case "SERVICE_TASK" -> services.put(key,new MinimalWorkflowDefinition.Service(key,n.path("action").textValue(),bindings(n.path("inputs")),bindings(n.path("outputs")),nodeIndex));
                default -> error(d,"UNSUPPORTED_FEATURE","/nodes");
            }
            nodeIndex++;
        }
        if (starts!=1 || humans!=1 || ends!=1) error(d,"INVALID_GRAPH","/nodes");
        if (d.failed()) return null;
        var outcome=human.path("outcomes").get(0);
        Map<String,MinimalWorkflowDefinition.Transition> outgoing=new HashMap<>();
        var transitions=new ArrayList<MinimalWorkflowDefinition.Transition>();
        var keys=new HashSet<String>();
        for (var t:root.path("transitions")) {
            var v=new MinimalWorkflowDefinition.Transition(t.path("key").textValue(),t.path("from").textValue(),t.path("to").textValue(),DefinitionDocumentCodec.string(t,"outcome"));
            if (!keys.add(v.key()) || outgoing.putIfAbsent(v.from(),v)!=null || !nodes.containsKey(v.from()) || !nodes.containsKey(v.to())
                    || v.from().equals(end.path("key").textValue())
                    || (v.from().equals(human.path("key").textValue()) ? !outcome.path("key").textValue().equals(v.outcome()) : v.outcome()!=null))
                error(d,"INVALID_GRAPH","/transitions");
            transitions.add(v);
        }
        var visited=new HashSet<String>(); String cursor=start.path("key").textValue();
        while (cursor!=null && visited.add(cursor)) {
            var edge=outgoing.get(cursor); cursor=edge==null ? null : edge.to();
        }
        if (cursor!=null || visited.size()!=nodes.size() || outgoing.size()!=nodes.size()-1 || !visited.contains(end.path("key").textValue()))
            error(d,"INVALID_GRAPH","/transitions");
        if (d.failed()) return null;
        return new MinimalWorkflowDefinition(new MinimalWorkflowDefinition.Node(start.path("key").textValue(),"START",start.path("name").textValue(),null),
                new MinimalWorkflowDefinition.Human(human.path("key").textValue(),human.path("taskType").textValue(),
                        new MinimalWorkflowDefinition.Assignment(human.path("assignment").path("resolver").textValue()),
                        new MinimalWorkflowDefinition.Outcome(outcome.path("key").textValue(),outcome.path("label").textValue(),outcome.path("requiresComment").booleanValue())),
                new MinimalWorkflowDefinition.Node(end.path("key").textValue(),"END",end.path("name").textValue(),end.path("result").textValue()),
                transitions,inputs,variables,initialize,services);
    }
    static void validate(ActionField field, ScalarValue value) {
        if (value.type()!=field.type() || (!field.nullable() && value.value()==NullLiteral.INSTANCE)) throw new IllegalArgumentException();
    }
    static void compatible(ActionField source, ActionField target) {
        if (source==null || target==null || source.type()!=target.type() || (source.nullable() && !target.nullable())) throw new IllegalArgumentException();
    }
    static void error(DefinitionDiagnostics d,String code,String path) {
        d.error(code,DiagnosticStage.CONTRACT,"Invalid scalar/service definition contract.",path);
    }
}
