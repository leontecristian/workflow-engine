package io.github.leontecristian.workflow.core.service.runtime;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionResult;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RuntimeServiceProgression {
    private final ServiceActionRegistry actions;
    private final RuntimeVariables variables;
    private final RuntimeJson json;
    private final io.github.leontecristian.workflow.core.service.expression.RestrictedExpressionService expressions;
    public RuntimeServiceProgression(ServiceActionRegistry actions,RuntimeVariables variables,RuntimeJson json) {
        this(actions,variables,json,new io.github.leontecristian.workflow.core.service.expression.RestrictedExpressionService());
    }
    @org.springframework.beans.factory.annotation.Autowired
    public RuntimeServiceProgression(ServiceActionRegistry actions,RuntimeVariables variables,RuntimeJson json,
            io.github.leontecristian.workflow.core.service.expression.RestrictedExpressionService expressions) {
        this.expressions=expressions;
        this.actions=actions; this.variables=variables; this.json=json;
    }
    MinimalWorkflowDefinition.Node traverse(MinimalWorkflowDefinition model,String from,String stop,ServiceActionContext context,
            Map<String,WorkflowValue> state,RuntimeProgression budget,RuntimeHistoryBatch batch) {
        RuntimeInputs.require(model.gateways().isEmpty(),Category.HOST_CONFIGURATION);
        return traverse(model,from,stop,context,state,budget,batch,"legacy");
    }
    MinimalWorkflowDefinition.Node traverse(MinimalWorkflowDefinition model,String from,String stop,ServiceActionContext context,
            Map<String,WorkflowValue> state,RuntimeProgression budget,RuntimeHistoryBatch batch,String definitionHash) {
        String cursor=from;
        while (true) {
            String current=cursor;
            var edge=model.gateways().contains(current) ? route(model,current,definitionHash,state,budget,batch)
                    : model.transitions().stream().filter(t -> t.from().equals(current)).findFirst()
                    .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
            Map<String,String> detail=current.equals(model.human().key()) ? Map.of("outcomeKey",model.human().outcome().key()) : Map.of();
            batch.next("TRANSITION_TAKEN",null,null,edge,detail,Map.of());
            cursor=edge.to();
            if (cursor.equals(stop)) return new MinimalWorkflowDefinition.Node(cursor,"HUMAN_TASK",null,null);
            if (model.ends().containsKey(cursor)) return model.ends().get(cursor);
            if (model.gateways().contains(cursor)) {
                budget.visit(new MinimalWorkflowDefinition.Node(cursor,"EXCLUSIVE_GATEWAY",null,null),"EXCLUSIVE_GATEWAY");
                continue;
            }
            var node=model.services().get(cursor);
            RuntimeInputs.require(node!=null,Category.HOST_CONFIGURATION);
            budget.visit(new MinimalWorkflowDefinition.Node(cursor,"SERVICE_TASK",null,null),"SERVICE_TASK");
            var descriptor=actions.descriptor(node.action());
            RuntimeInputs.require(descriptor!=null,Category.HOST_CONFIGURATION);
            Map<String,WorkflowValue> args=new java.util.TreeMap<>();
            for (var f:descriptor.inputs()) {
                var b=node.inputs().get(f.key());
                RuntimeInputs.require(b!=null || !f.required(),Category.HOST_CONFIGURATION);
                if (b!=null) {
                    var value=state.get(b.key()); RuntimeInputs.require(value!=null,Category.UNINITIALIZED_VARIABLE);
                    RuntimeValueBindings.validate(f,value,Category.HOST_CONFIGURATION); args.put(f.key(),value);
                }
            }
            RuntimeValueBindings.envelope(json.encode(RuntimeValueBindings.tokens(args,json)));
            ServiceActionResult returned;
            try {
                returned=actions.action(node.action()).execute(new ServiceActionContext(context.commandId(),context.instanceId(),context.definitionVersionId(),
                        cursor,context.businessType(),context.businessId(),context.commandContext()),Map.copyOf(args));
            } catch (Throwable failure) { throw new WorkflowExecutionException(Category.SERVICE_ACTION_FAILED,failure); }
            RuntimeInputs.require(returned!=null,Category.INVALID_ACTION_OUTPUT);
            RuntimeValueBindings.output(descriptor.outputs(),returned.outputs(),json);
            var writes=RuntimeValueBindings.prepare(node.outputs(),model.variables(),state,returned.outputs(),Category.INVALID_ACTION_OUTPUT);
            var success=json.object(); success.put("nodeKey",cursor); success.put("action",node.action());
            success.set("inputs",RuntimeValueBindings.tokens(args,json)); success.set("outputs",RuntimeValueBindings.tokens(returned.outputs(),json));
            batch.next("SERVICE_TASK_COMPLETED",null,null,null,success,Map.of("nodeKey",cursor,"action",node.action()));
            variables.apply(context.instanceId(),cursor,node.outputs(),state,writes,batch);
        }
    }
    private MinimalWorkflowDefinition.Transition route(MinimalWorkflowDefinition model,String node,String hash,
            Map<String,WorkflowValue> state,RuntimeProgression budget,RuntimeHistoryBatch batch) {
        var snapshot=Map.copyOf(state);
        var details=json.object(); details.put("nodeKey",node); var evaluations=details.putArray("evaluations");
        MinimalWorkflowDefinition.Transition selected=null, fallback=null;
        long evidenceUtf16=0, evidenceUtf8=0;
        for (var transition:model.transitions()) {
            if (!transition.from().equals(node)) continue;
            if (transition.defaultRoute()) { fallback=transition; continue; }
            io.github.leontecristian.workflow.core.dto.expression.ExpressionEvaluation evaluated;
            try { evaluated=expressions.evaluate(hash,transition.key(),transition.condition(),model.variables(),snapshot,budget.expressions()); }
            catch (io.github.leontecristian.workflow.core.exception.expression.ExpressionException failure) {
                Category category=switch(failure.kind()) {
                    case UNINITIALIZED -> Category.UNINITIALIZED_VARIABLE;
                    case LIMIT -> Category.EXPRESSION_LIMIT_EXCEEDED;
                    case INVALID -> Category.HOST_CONFIGURATION;
                    case UNSUPPORTED -> Category.UNSUPPORTED_VERSION;
                    case EVALUATION -> Category.EXPRESSION_EVALUATION_FAILED;
                };
                throw new WorkflowExecutionException(category,failure);
            }
            var evaluation=evaluations.addObject(); evaluation.put("transitionKey",transition.key()); evaluation.put("result",evaluated.result());
            var reads=evaluation.putArray("reads");
            for (var read:evaluated.reads()) {
                var entry=reads.addObject(); entry.put("variableKey",read.variableKey()); entry.put("type",read.type().name());
                entry.put("state",read.state()); entry.set("value",json.tree(read.valueJson()));
                var path=entry.putArray("path");
                for (var segment:read.path()) {
                    var part=path.addObject(); part.put("kind",segment.kind());
                    if (segment.key()!=null) part.put("key",segment.key()); else part.put("index",segment.index());
                }
            }
            String retained=json.history(evaluation);
            evidenceUtf16+=retained.length()*2L;
            evidenceUtf8+=retained.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            RuntimeInputs.require(evidenceUtf16<=4*1024*1024 && evidenceUtf8<=4*1024*1024,Category.LIMIT_EXCEEDED);
            if(evaluated.result()) { selected=transition; break; }
        }
        boolean usedDefault=selected==null;
        if(usedDefault) selected=fallback;
        RuntimeInputs.require(selected!=null,Category.ROUTE_NOT_FOUND);
        details.put("selectedTransitionKey",selected.key()); details.put("usedDefault",usedDefault);
        batch.next("GATEWAY_ROUTED",null,null,null,details,Map.of("nodeKey",node,"selectedTransitionKey",selected.key()));
        return selected;
    }
}
