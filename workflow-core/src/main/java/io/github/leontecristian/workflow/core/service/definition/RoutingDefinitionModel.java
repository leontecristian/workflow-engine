package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.leontecristian.workflow.core.dto.definition.DiagnosticStage;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Assignment;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Binding;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Human;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Input;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Node;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Outcome;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Service;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Transition;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.exception.expression.ExpressionException;
import io.github.leontecristian.workflow.core.service.expression.RestrictedExpressionService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Acyclic single-position branching. Structural checks never predict a condition's result. */
final class RoutingDefinitionModel {
    private RoutingDefinitionModel() { }
    static MinimalWorkflowDefinition load(JsonNode root, DefinitionDiagnostics d, Map<String,Input> inputs,
            Map<String,ActionField> variables, Map<String,Binding> initialize, Map<String,Service> services,
            RestrictedExpressionService expressions) {
        try { if (MinimalDefinitionLoader.routing(root)) expressions.requireCompatible(); }
        catch(ExpressionException failure) {
            d.error("EXPRESSION_VERSION_UNSUPPORTED",DiagnosticStage.CONTRACT,"Unsupported restricted expression implementation.","/nodes");
            return null;
        }
        Map<String,JsonNode> nodes = new LinkedHashMap<>();
        Map<String,Node> ends = new LinkedHashMap<>(); Set<String> gateways = new HashSet<>();
        JsonNode start = null, human = null; int starts = 0, humans = 0, nodeIndex = 0, humanIndex = -1;
        for (var n : root.path("nodes")) {
            String key = n.path("key").textValue();
            if (nodes.putIfAbsent(key,n) != null) d.error("DUPLICATE_KEY", DiagnosticStage.GRAPH, "Node keys must be distinct.", "/nodes/" + nodeIndex + "/key");
            switch (n.path("type").textValue()) {
                case "START" -> { start=n; starts++; }
                case "HUMAN_TASK" -> { human=n; humans++; humanIndex=nodeIndex; }
                case "END" -> ends.put(key,new Node(key,"END",n.path("name").textValue(),n.path("result").textValue()));
                case "EXCLUSIVE_GATEWAY" -> gateways.add(key);
                case "SERVICE_TASK" -> { }
                default -> graph(d,"/nodes");
            }
            nodeIndex++;
        }
        if (starts!=1 || humans!=1 || ends.isEmpty()) graph(d,"/nodes");
        if (d.failed()) return null;
        String startKey=start.path("key").textValue(), humanKey=human.path("key").textValue();
        var outcomes = new ArrayList<Outcome>();
        var outcomeKeys = new HashSet<String>();
        String outcomePointer = "/nodes/" + humanIndex + "/outcomes/";
        int outcomeIndex = 0;
        for (var declared : human.path("outcomes")) {
            String key = declared.path("key").textValue();
            if (!outcomeKeys.add(key)) d.error("DUPLICATE_KEY", DiagnosticStage.GRAPH,
                    "Outcome keys must be distinct.", outcomePointer + outcomeIndex + "/key");
            outcomes.add(new Outcome(key, declared.path("label").textValue(), declared.path("requiresComment").asBoolean()));
            outcomeIndex++;
        }
        String resolver = human.path("assignment").path("resolver").textValue();
        if (!DefinitionInputs.text(resolver, 100, 400, true))
            d.error("INVALID_STRUCTURE", DiagnosticStage.CONTRACT, "Resolver must be a bounded logical identifier.", "/nodes/" + humanIndex + "/assignment/resolver");
        Map<String,List<Transition>> outgoing=new HashMap<>();
        var transitions=new ArrayList<Transition>(); var keys=new HashSet<String>(); var routedOutcomes = new HashSet<String>(); int i=0;
        for (var t:root.path("transitions")) {
            String pointer="/transitions/"+i++;
            var edge=new Transition(t.path("key").textValue(),t.path("from").textValue(),t.path("to").textValue(),
                    DefinitionDocumentCodec.string(t,"outcome"),DefinitionDocumentCodec.string(t,"condition"),t.path("default").asBoolean());
            if (!keys.add(edge.key())) d.error("DUPLICATE_KEY", DiagnosticStage.GRAPH, "Transition keys must be distinct.", pointer + "/key");
            if (!nodes.containsKey(edge.from()) || !nodes.containsKey(edge.to()) || edge.to().equals(startKey)) graph(d,pointer);
            if (edge.from().equals(humanKey) && !routedOutcomes.add(edge.outcome())) graph(d, pointer + "/outcome");
            boolean gateway=gateways.contains(edge.from());
            if (gateway) {
                if (edge.outcome()!=null || (edge.condition()!=null)==edge.defaultRoute() || (edge.condition()!=null && edge.condition().isBlank())) graph(d,pointer);
            } else if (t.has("condition") || t.has("default") || (edge.from().equals(humanKey) ? !outcomeKeys.contains(edge.outcome()) : edge.outcome()!=null)) graph(d,pointer);
            if (gateway && edge.condition()!=null) {
                try { expressions.validate(edge.condition(),variables); }
                catch (ExpressionException failure) {
                    d.error(failure.kind()==ExpressionException.Kind.LIMIT ? "EXPRESSION_LIMIT_EXCEEDED" : "INVALID_EXPRESSION",
                            DiagnosticStage.CONTRACT,"Invalid restricted condition.",pointer+"/condition");
                }
            }
            outgoing.computeIfAbsent(edge.from(),ignored->new ArrayList<>()).add(edge); transitions.add(edge);
        }
        for (String key:nodes.keySet()) {
            var edges=outgoing.getOrDefault(key,List.of());
            if (ends.containsKey(key)) {
                if (!edges.isEmpty()) graph(d,"/transitions");
            } else if (gateways.contains(key)) {
                if (edges.isEmpty() || edges.stream().filter(Transition::defaultRoute).count()>1) graph(d,"/transitions");
            } else if (edges.size() != (key.equals(humanKey) ? outcomes.size() : 1)) graph(d,"/transitions");
        }
        var humanEdges = outgoing.getOrDefault(humanKey, List.of());
        for (int index = 0; index < outcomes.size(); index++) {
            String key = outcomes.get(index).key();
            if (humanEdges.stream().filter(edge -> key.equals(edge.outcome())).count() != 1)
                graph(d, outcomePointer + index);
        }
        if (d.failed()) return null;
        var colors=new HashMap<String,Integer>();
        for(String key:nodes.keySet()) if(cyclic(key,outgoing,colors)) { graph(d,"/transitions"); break; }
        Set<String> reachable=reach(startKey,outgoing,null);
        if(reachable.size()!=nodes.size() || reach(startKey,outgoing,humanKey).stream().anyMatch(ends::containsKey)) graph(d,"/transitions");
        Map<String,List<Transition>> reverse=new HashMap<>();
        for(var edge:transitions) reverse.computeIfAbsent(edge.to(),ignored->new ArrayList<>()).add(new Transition(edge.key(),edge.to(),edge.from(),null));
        var reachesEnd=new HashSet<String>(); for(String end:ends.keySet()) reachesEnd.addAll(reach(end,reverse,null));
        if(reachesEnd.size()!=nodes.size()) graph(d,"/transitions");
        if(d.failed()) return null;
        return new MinimalWorkflowDefinition(new Node(startKey,"START",start.path("name").textValue(),null),
                new Human(humanKey,human.path("taskType").textValue(),new Assignment(resolver), outcomes),
                ends.values().iterator().next(),transitions,inputs,variables,initialize,services,ends,gateways);
    }
    private static Set<String> reach(String start,Map<String,List<Transition>> outgoing,String stop) {
        var found=new HashSet<String>(); var pending=new ArrayDeque<String>(); pending.add(start);
        while(!pending.isEmpty()) {
            String key=pending.removeFirst();
            if(!found.add(key) || key.equals(stop)) continue;
            for(var edge:outgoing.getOrDefault(key,List.of())) pending.add(edge.to());
        }
        return found;
    }
    private static boolean cyclic(String key,Map<String,List<Transition>> outgoing,Map<String,Integer> colors) {
        if(colors.getOrDefault(key,0)==1) return true;
        if(colors.getOrDefault(key,0)==2) return false;
        colors.put(key,1);
        for(var edge:outgoing.getOrDefault(key,List.of())) if(cyclic(edge.to(),outgoing,colors)) return true;
        colors.put(key,2); return false;
    }
    private static void graph(DefinitionDiagnostics d,String pointer) { d.error("INVALID_GRAPH",DiagnosticStage.GRAPH,"Invalid workflow graph.",pointer); }
}
