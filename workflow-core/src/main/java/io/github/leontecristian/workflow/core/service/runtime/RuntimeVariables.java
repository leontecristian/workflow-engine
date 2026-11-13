package io.github.leontecristian.workflow.core.service.runtime;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Binding;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowVariableEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowVariableId;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowVariableRepository;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** All writes are called only after new allocation or the early flushed aggregate claim. */
@Component
public class RuntimeVariables {
    private final WorkflowVariableRepository repository;
    private final EntityManager entityManager;
    private final RuntimeJson json;
    public RuntimeVariables(WorkflowVariableRepository repository,EntityManager entityManager,RuntimeJson json) {
        this.repository=repository; this.entityManager=entityManager; this.json=json;
    }
    Map<String,WorkflowValue> read(long instanceId,MinimalWorkflowDefinition model) {
        Map<String,WorkflowValue> result=new TreeMap<>();
        var rows=repository.findByInstanceId(instanceId,org.springframework.data.domain.PageRequest.of(0,101));
        RuntimeInputs.require(rows.size()<=100,Category.HOST_CONFIGURATION);
        for (var row:rows) {
            entityManager.refresh(row);
            String key=row.getId().getVariableKey();
            RuntimeInputs.require(model.variables().containsKey(key),Category.HOST_CONFIGURATION);
            var value=row.executionValue(); RuntimeValueBindings.validate(model.variables().get(key),value,Category.HOST_CONFIGURATION);
            result.put(key,value);
        }
        return result;
    }
    void apply(long instanceId,String nodeKey,Map<String,Binding> bindings,Map<String,WorkflowValue> before,
            Map<String,WorkflowValue> writes,RuntimeHistoryBatch batch) {
        apply(instanceId,nodeKey,bindings,before,writes,batch,null,null);
    }
    Map<String,WorkflowValue> afterTaskCompletion(long instanceId, MinimalWorkflowDefinition model,
            MinimalWorkflowDefinition.Human human, WorkflowJsonValue result, long activityId, long taskId, RuntimeHistoryBatch batch) {
        return afterTaskResolution(instanceId,model,human,result,activityId,taskId,batch);
    }
    Map<String,WorkflowValue> afterTaskResolution(long instanceId, MinimalWorkflowDefinition model,
            MinimalWorkflowDefinition.Human human, WorkflowJsonValue result, long activityId, Long taskId,
            RuntimeHistoryBatch batch) {
        String key = human.outputVariable();
        if (key == null || result == null) return read(instanceId, model);
        RuntimeInputs.require(repository.findById(new WorkflowVariableId(instanceId,key)).isEmpty(),Category.CONFLICT);
        var state = read(instanceId, model);
        RuntimeInputs.require(!state.containsKey(key),Category.CONFLICT);
        var writes = Map.of(key,new WorkflowValue(VariableValueType.JSON,result));
        apply(instanceId,human.key(),Map.of(key,new Binding(taskId == null ? "activitySubmissions" : "taskResult",human.key(),null)),
                state,writes,batch,activityId,taskId);
        return state;
    }
    private void apply(long instanceId,String nodeKey,Map<String,Binding> bindings,Map<String,WorkflowValue> before,
            Map<String,WorkflowValue> writes,RuntimeHistoryBatch batch,Long activityId,Long taskId) {
        Map<String,WorkflowValue> after=new TreeMap<>(before); after.putAll(writes);
        RuntimeInputs.require(json.encode(RuntimeValueBindings.tokens(after,json)).length()*2L<=RuntimeJson.MAP_UTF16_BYTES,Category.LIMIT_EXCEEDED);
        if (writes.isEmpty()) return;
        var orderedWrites=new TreeMap<>(writes);
        var details=json.object(); details.put("nodeKey",nodeKey);
        var entries=details.putArray("writes");
        for (var e:orderedWrites.entrySet()) {
            var old=before.get(e.getKey()); var value=e.getValue();
            var entry=entries.addObject(); entry.put("variableKey",e.getKey()); entry.put("type",value.type().name());
            entry.set("source",RuntimeValueBindings.source(bindings.get(e.getKey()),json)); entry.put("previousInitialized",old!=null);
            entry.set("previousValue",old==null ? com.fasterxml.jackson.databind.node.NullNode.instance : RuntimeValueBindings.token(old.value()));
            entry.set("value",RuntimeValueBindings.token(value.value()));
        }
        // Check complete per-record/cumulative evidence before changing any variable. The history
        // append and subsequent writes participate in the same host transaction and roll back together.
        batch.next("VARIABLES_WRITTEN",activityId,taskId,details,
                Map.of("nodeKey",nodeKey,"writeCount",Integer.toString(orderedWrites.size())),
                (short)(activityId != null && taskId == null ? 3 : 2));
        for (var e:orderedWrites.entrySet()) {
            var row=activityId==null ? repository.findById(new WorkflowVariableId(instanceId,e.getKey())).orElse(null) : null;
            if(row==null) entityManager.persist(new WorkflowVariableEntity(instanceId,e.getKey(),e.getValue()));
            else row.replace(e.getValue());
        }
        before.clear(); before.putAll(after);
    }
}
