package io.github.leontecristian.workflow.core.service.runtime;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Binding;
import io.github.leontecristian.workflow.core.dto.runtime.ScalarValue;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowVariableEntity;
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
    Map<String,ScalarValue> read(long instanceId,MinimalWorkflowDefinition model) {
        Map<String,ScalarValue> result=new TreeMap<>();
        var rows=repository.findByInstanceId(instanceId,org.springframework.data.domain.PageRequest.of(0,101));
        RuntimeInputs.require(rows.size()<=100,Category.HOST_CONFIGURATION);
        for (var row:rows) {
            entityManager.refresh(row);
            String key=row.getId().getVariableKey();
            RuntimeInputs.require(model.variables().containsKey(key),Category.HOST_CONFIGURATION);
            var value=row.scalar(); RuntimeScalarBindings.validate(model.variables().get(key),value,Category.HOST_CONFIGURATION);
            result.put(key,value);
        }
        return result;
    }
    void apply(long instanceId,String nodeKey,Map<String,Binding> bindings,Map<String,ScalarValue> before,
            Map<String,ScalarValue> writes,RuntimeHistoryBatch batch) {
        Map<String,ScalarValue> after=new TreeMap<>(before); after.putAll(writes);
        RuntimeInputs.require(json.encode(RuntimeScalarBindings.tokens(after,json)).length()*2L<=1024*1024,Category.LIMIT_EXCEEDED);
        for (var e:new TreeMap<>(writes).entrySet()) {
            var old=before.get(e.getKey()); var value=e.getValue();
            var details=json.object(); details.put("nodeKey",nodeKey); details.put("variableKey",e.getKey()); details.put("type",value.type().name());
            details.set("source",RuntimeScalarBindings.source(bindings.get(e.getKey()),json)); details.put("previousInitialized",old!=null);
            details.set("previousValue",old==null ? com.fasterxml.jackson.databind.node.NullNode.instance : RuntimeScalarBindings.token(old.value()));
            details.set("value",RuntimeScalarBindings.token(value.value()));
            // Validate complete required evidence before mutating this value.
            json.evidence(details);
            var id=new io.github.leontecristian.workflow.core.entity.runtime.WorkflowVariableId(instanceId,e.getKey());
            var row=repository.findById(id).orElse(null);
            if(row==null) entityManager.persist(new WorkflowVariableEntity(instanceId,e.getKey(),value)); else row.replace(value);
            batch.next("VARIABLE_WRITTEN",null,null,null,details,Map.of("nodeKey",nodeKey,"variableKey",e.getKey()));
        }
        before.clear(); before.putAll(after);
    }
}
