package io.github.leontecristian.workflow.core.service.runtime;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.runtime.ScalarValue;
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
    public RuntimeServiceProgression(ServiceActionRegistry actions,RuntimeVariables variables,RuntimeJson json) {
        this.actions=actions; this.variables=variables; this.json=json;
    }
    void traverse(MinimalWorkflowDefinition model,String from,String stop,ServiceActionContext context,
            Map<String,ScalarValue> state,RuntimeProgression budget,RuntimeHistoryBatch batch) {
        String cursor=from;
        while (true) {
            String current=cursor;
            var edge=model.transitions().stream().filter(t -> t.from().equals(current)).findFirst()
                    .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
            Map<String,String> detail=current.equals(model.human().key()) ? Map.of("outcomeKey",model.human().outcome().key()) : Map.of();
            batch.next("TRANSITION_TAKEN",null,null,edge,detail,Map.of());
            cursor=edge.to();
            if (cursor.equals(stop)) return;
            var node=model.services().get(cursor);
            RuntimeInputs.require(node!=null,Category.HOST_CONFIGURATION);
            budget.visit(new MinimalWorkflowDefinition.Node(cursor,"SERVICE_TASK",null,null),"SERVICE_TASK");
            var descriptor=actions.descriptor(node.action());
            RuntimeInputs.require(descriptor!=null,Category.HOST_CONFIGURATION);
            Map<String,ScalarValue> args=new java.util.TreeMap<>();
            for (var f:descriptor.inputs()) {
                var b=node.inputs().get(f.key());
                RuntimeInputs.require(b!=null || !f.required(),Category.HOST_CONFIGURATION);
                if (b!=null) {
                    var value=state.get(b.key()); RuntimeInputs.require(value!=null,Category.UNINITIALIZED_VARIABLE);
                    RuntimeScalarBindings.validate(f,value,Category.HOST_CONFIGURATION); args.put(f.key(),value);
                }
            }
            RuntimeInputs.require(RuntimeJson.fits(json.encode(RuntimeScalarBindings.tokens(args,json)),64*1024),Category.LIMIT_EXCEEDED);
            ServiceActionResult returned;
            try {
                returned=actions.action(node.action()).execute(new ServiceActionContext(context.commandId(),context.instanceId(),context.definitionVersionId(),
                        cursor,context.businessType(),context.businessId(),context.commandContext()),Map.copyOf(args));
            } catch (Throwable failure) { throw new WorkflowExecutionException(Category.SERVICE_ACTION_FAILED,failure); }
            RuntimeInputs.require(returned!=null,Category.INVALID_ACTION_OUTPUT);
            RuntimeScalarBindings.output(descriptor.outputs(),returned.outputs(),json);
            var writes=RuntimeScalarBindings.prepare(node.outputs(),model.variables(),state,returned.outputs(),Category.INVALID_ACTION_OUTPUT);
            var success=json.object(); success.put("nodeKey",cursor); success.put("action",node.action());
            success.set("inputs",RuntimeScalarBindings.tokens(args,json)); success.set("outputs",RuntimeScalarBindings.tokens(returned.outputs(),json));
            batch.next("SERVICE_TASK_COMPLETED",null,null,null,success,Map.of("nodeKey",cursor,"action",node.action()));
            variables.apply(context.instanceId(),cursor,node.outputs(),state,writes,batch);
        }
    }
}
