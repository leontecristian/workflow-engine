package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.leontecristian.workflow.core.dto.definition.DiagnosticStage;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Assignment;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Binding;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Human;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.HumanInput;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Input;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Node;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Outcome;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Service;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Transition;
import io.github.leontecristian.workflow.core.dto.definition.document.TaskResultContract;
import io.github.leontecristian.workflow.core.dto.definition.document.PreviousDecisionsPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.CompletionPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.TaskClaimPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
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
            RestrictedExpressionService expressions, boolean authoring) {
        try { if (MinimalDefinitionLoader.routing(root)) expressions.requireCompatible(); }
        catch(ExpressionException failure) {
            d.error("EXPRESSION_VERSION_UNSUPPORTED",DiagnosticStage.CONTRACT,"Unsupported restricted expression implementation.","/nodes");
            return null;
        }
        PreviousDecisionsPolicy previousDecisions = previousDecisions(root.path("previousDecisions"), PreviousDecisionsPolicy.NONE);
        Map<String,JsonNode> nodes = new LinkedHashMap<>();
        Map<String,Node> ends = new LinkedHashMap<>(); Set<String> gateways = new HashSet<>();
        Map<String,Human> humans = new LinkedHashMap<>();
        Map<String,String> namedOutputs = new HashMap<>();
        var schemaBudget = new TaskResultSchema.DefinitionBudget();
        Map<String,Integer> humanIndices = new HashMap<>();
        JsonNode start = null; int starts = 0, nodeIndex = 0;
        for (var n : root.path("nodes")) {
            String key = n.path("key").textValue();
            if (nodes.putIfAbsent(key,n) != null) d.error("DUPLICATE_KEY", DiagnosticStage.GRAPH, "Node keys must be distinct.", "/nodes/" + nodeIndex + "/key");
            switch (n.path("type").textValue()) {
                case "START" -> { start=n; starts++; }
                case "HUMAN_TASK" -> {
                    var outcomes = new ArrayList<Outcome>();
                    var outcomeKeys = new HashSet<String>();
                    int outcomeIndex = 0;
                    for (var declared : n.path("outcomes")) {
                        String outcomeKey = declared.path("key").textValue();
                        if (!outcomeKeys.add(outcomeKey)) d.error("DUPLICATE_KEY", DiagnosticStage.GRAPH,
                                "Outcome keys must be distinct.", "/nodes/" + nodeIndex + "/outcomes/" + outcomeIndex + "/key");
                        outcomes.add(new Outcome(outcomeKey, declared.path("label").textValue(), declared.path("requiresComment").asBoolean()));
                        outcomeIndex++;
                    }
                    String resolver = n.path("assignment").path("resolver").textValue();
                    if (!DefinitionInputs.text(resolver, 100, 400, true))
                        d.error("INVALID_STRUCTURE", DiagnosticStage.CONTRACT, "Resolver must be a bounded logical identifier.", "/nodes/" + nodeIndex + "/assignment/resolver");
                    CompletionPolicy completionPolicy = CompletionPolicy.valueOf(n.path("completion").path("policy").textValue());
                    String contributionOutcome = n.path("completion").path("contributionOutcome").textValue();
                    if (completionPolicy == CompletionPolicy.ALL && !outcomeKeys.contains(contributionOutcome)) {
                        d.error("INVALID_STRUCTURE", DiagnosticStage.CONTRACT,
                                "Contribution outcome must identify a declared outcome.",
                                "/nodes/" + nodeIndex + "/completion/contributionOutcome");
                    }
                    String claimPolicy = n.path("claimPolicy").asText("OPTIONAL");
                    var resultContract = TaskResultSchema.readContract(n.path("resultContract"),
                            "/nodes/" + nodeIndex + "/resultContract", d, schemaBudget, authoring);
                    String outputVariable = n.path("outputVariable").textValue();
                    if (outputVariable != null) {
                        addNamedOutput(outputVariable, key, resultContract, "/nodes/" + nodeIndex + "/outputVariable",
                                variables, namedOutputs, d);
                    }
                    humans.put(key,new Human(key,n.path("taskType").textValue(),new Assignment(resolver),outcomes,
                            TaskClaimPolicy.valueOf(claimPolicy), resultContract, outputVariable, Map.of(), completionPolicy, contributionOutcome,
                            previousDecisions(n.path("previousDecisions"), previousDecisions), n.path("name").textValue()));
                    humanIndices.put(key,nodeIndex);
                }
                case "END" -> ends.put(key,new Node(key,"END",n.path("name").textValue(),n.path("result").textValue()));
                case "EXCLUSIVE_GATEWAY" -> gateways.add(key);
                case "SERVICE_TASK" -> { }
                default -> graph(d,"/nodes");
            }
            nodeIndex++;
        }
        if (variables.size() > 100) {
            d.error("LIMIT_EXCEEDED", DiagnosticStage.CONTRACT, "Too many declared variables and named outputs.", "/variables");
        }
        if (starts!=1 || humans.isEmpty() || ends.isEmpty()) graph(d,"/nodes");
        if (d.failed()) return null;
        String startKey=start.path("key").textValue();
        Map<String,List<Transition>> outgoing=new HashMap<>();
        var transitions=new ArrayList<Transition>(); var keys=new HashSet<String>(); int i=0;
        for (var t:root.path("transitions")) {
            String pointer="/transitions/"+i++;
            var edge=new Transition(t.path("key").textValue(),t.path("from").textValue(),t.path("to").textValue(),
                    DefinitionDocumentCodec.string(t,"outcome"),DefinitionDocumentCodec.string(t,"condition"),t.path("default").asBoolean());
            if (!keys.add(edge.key())) d.error("DUPLICATE_KEY", DiagnosticStage.GRAPH, "Transition keys must be distinct.", pointer + "/key");
            if (!nodes.containsKey(edge.from()) || !nodes.containsKey(edge.to()) || edge.to().equals(startKey)) graph(d,pointer);
            var human = humans.get(edge.from());
            boolean gateway=gateways.contains(edge.from());
            if (gateway) {
                if (edge.outcome()!=null || (edge.condition()!=null)==edge.defaultRoute() || (edge.condition()!=null && edge.condition().isBlank())) graph(d,pointer);
            } else if (t.has("condition") || t.has("default") || (human != null ? human.outcome(edge.outcome()).isEmpty() : edge.outcome()!=null)) graph(d,pointer);
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
            } else if (!humans.containsKey(key) && edges.size() != 1) graph(d,"/transitions");
        }
        for (var human : humans.values()) {
            var outcomes = human.outcomes();
            String outcomePointer = "/nodes/" + humanIndices.get(human.key()) + "/outcomes/";
            var humanEdges = outgoing.getOrDefault(human.key(), List.of());
            for (int index = 0; index < outcomes.size(); index++) {
                String key = outcomes.get(index).key();
                if (humanEdges.stream().filter(edge -> key.equals(edge.outcome())).count() != 1)
                    graph(d, outcomePointer + index);
            }
        }
        if (d.failed()) return null;
        var colors=new HashMap<String,Integer>();
        for(String key:nodes.keySet()) if(cyclic(key,outgoing,colors)) { graph(d,"/transitions"); break; }
        Set<String> reachable=reach(startKey,outgoing,Set.of());
        if(reachable.size()!=nodes.size() || reach(startKey,outgoing,humans.keySet()).stream().anyMatch(ends::containsKey)) graph(d,"/transitions");
        Map<String,List<Transition>> reverse=new HashMap<>();
        for(var edge:transitions) reverse.computeIfAbsent(edge.to(),ignored->new ArrayList<>()).add(new Transition(edge.key(),edge.to(),edge.from(),null));
        var reachesEnd=new HashSet<String>(); for(String end:ends.keySet()) reachesEnd.addAll(reach(end,reverse,Set.of()));
        if(reachesEnd.size()!=nodes.size()) graph(d,"/transitions");
        if(d.failed()) return null;
        for (var human : humans.values()) {
            var selectors = human.previousDecisions().nodeKeys();
            for (int index = 0; index < selectors.size(); index++) {
                String source = selectors.get(index);
                if (source.equals(human.key()) || !humans.containsKey(source)
                        || !reach(source, outgoing, Set.of()).contains(human.key())) {
                    graph(d, "/nodes/" + humanIndices.get(human.key()) + "/previousDecisions/nodeKeys/" + index);
                }
            }
        }
        if (d.failed()) return null;
        // All source contracts and named-output descriptors are known, regardless of document node order.
        for (var human : List.copyOf(humans.values())) {
            var bindings = ExecutionValueDefinitionModel.bindings(nodes.get(human.key()).path("inputs"));
            if (bindings.isEmpty()) continue;
            var humanInputs = humanInputs(bindings, variables, humans, d,
                    "/nodes/" + humanIndices.get(human.key()) + "/inputs/");
            humans.put(human.key(), new Human(human.key(), human.taskType(), human.assignment(), human.outcomes(),
                    human.claimPolicy(), human.resultContract(), human.outputVariable(), humanInputs,
                    human.completionPolicy(), human.contributionOutcome(), human.previousDecisions(), human.name()));
        }
        if(d.failed()) return null;
        return new MinimalWorkflowDefinition(new Node(startKey,"START",start.path("name").textValue(),null),
                humans,
                ends.values().iterator().next(),transitions,inputs,variables,initialize,services,ends,gateways,namedOutputs,previousDecisions);
    }

    private static PreviousDecisionsPolicy previousDecisions(JsonNode setting, PreviousDecisionsPolicy inherited) {
        if (setting.isMissingNode() || "INHERIT".equals(setting.path("mode").textValue())) return inherited;
        var mode = PreviousDecisionsPolicy.Mode.valueOf(setting.path("mode").textValue());
        var keys = new ArrayList<String>();
        setting.path("nodeKeys").forEach(key -> keys.add(key.textValue()));
        return new PreviousDecisionsPolicy(mode, keys);
    }

    private static Map<String,HumanInput> humanInputs(Map<String,Binding> bindings, Map<String,ActionField> variables,
            Map<String,Human> humans, DefinitionDiagnostics diagnostics, String pointer) {
        Map<String,HumanInput> inputs = new HashMap<>();
        for (var entry : bindings.entrySet()) {
            var binding = entry.getValue();
            if ("variable".equals(binding.source())) {
                var variable = variables.get(binding.key());
                if (variable == null) {
                    diagnostics.error("INVALID_BINDING", DiagnosticStage.CONTRACT,
                            "Human input must reference a declared variable or named output.", pointer + entry.getKey());
                } else {
                    inputs.put(entry.getKey(), new HumanInput(binding, variable.type(), variable.nullable()));
                }
            } else {
                var source = humans.get(binding.nodeKey());
                if (source == null || source.resultContract().mode() == TaskResultContract.Mode.FORBIDDEN) {
                    diagnostics.error("INVALID_BINDING", DiagnosticStage.CONTRACT,
                            "Submission source must be a human node with an optional or required result.",
                            pointer + entry.getKey());
                } else {
                    inputs.put(entry.getKey(), new HumanInput(binding, VariableValueType.JSON, false, source.completionPolicy() == io.github.leontecristian.workflow.core.dto.runtime.CompletionPolicy.ALL));
                }
            }
        }
        return inputs;
    }

    private static void addNamedOutput(String name, String nodeKey, TaskResultContract contract, String pointer,
            Map<String,ActionField> variables, Map<String,String> namedOutputs, DefinitionDiagnostics diagnostics) {
        if (contract.mode() == TaskResultContract.Mode.FORBIDDEN) {
            diagnostics.error("INVALID_VARIABLE", DiagnosticStage.CONTRACT,
                    "A named output requires an optional or required task result.", pointer);
            return;
        }
        ActionField descriptor;
        try {
            descriptor = new ActionField(name, VariableValueType.JSON, false, false);
        } catch (IllegalArgumentException invalidName) {
            diagnostics.error("INVALID_VARIABLE", DiagnosticStage.CONTRACT, "Invalid variable name.", pointer);
            return;
        }
        if (variables.putIfAbsent(name, descriptor) != null) {
            diagnostics.error("INVALID_VARIABLE", DiagnosticStage.CONTRACT,
                    "Variable names must have distinct producers.", pointer);
            return;
        }
        namedOutputs.put(name, nodeKey);
    }

    private static Set<String> reach(String start,Map<String,List<Transition>> outgoing,Set<String> stop) {
        var found=new HashSet<String>(); var pending=new ArrayDeque<String>(); pending.add(start);
        while(!pending.isEmpty()) {
            String key=pending.removeFirst();
            if(!found.add(key) || stop.contains(key)) continue;
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
