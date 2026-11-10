package io.github.leontecristian.workflow.core.service.definition;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.document.TaskResultContract;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.service.runtime.ServiceActionRegistry;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final class ServiceBindingValidation {
    private ServiceBindingValidation() {}
    static void check(MinimalWorkflowDefinition model, ServiceActionRegistry registry, DefinitionDiagnostics d) {
        for (var node:model.services().values()) {
            var descriptor=registry.descriptor(node.action());
            String path="/nodes/"+node.sourceIndex();
            if (descriptor==null) { ExecutionValueDefinitionModel.error(d,"HOST_CAPABILITY_UNAVAILABLE",path+"/action"); continue; }
            Map<String,ActionField> args=descriptor.inputs().stream().collect(Collectors.toMap(ActionField::key,Function.identity()));
            Map<String,ActionField> outputs=descriptor.outputs().stream().collect(Collectors.toMap(ActionField::key,Function.identity()));
            for (var f:args.values()) if (f.required() && !node.inputs().containsKey(f.key())) ExecutionValueDefinitionModel.error(d,"ACTION_CONTRACT",path+"/inputs");
            for (var e:node.inputs().entrySet()) {
                try {
                    var binding = e.getValue();
                    var target = args.get(e.getKey());
                    if ("submission".equals(binding.source())) {
                        var human = model.humans().get(binding.nodeKey());
                        if (human == null || human.resultContract().mode() == TaskResultContract.Mode.FORBIDDEN
                                || target == null || target.type() != VariableValueType.JSON) {
                            throw new IllegalArgumentException();
                        }
                    } else {
                        ExecutionValueDefinitionModel.compatible(model.variables().get(binding.key()), target);
                    }
                }
                catch (RuntimeException ex) { ExecutionValueDefinitionModel.error(d,"INVALID_BINDING",path+"/inputs/"+e.getKey()); }
            }
            for (var e:node.outputs().entrySet()) {
                try {
                    var b=e.getValue();
                    ExecutionValueDefinitionModel.compatible((b.source().equals("variable") ? model.variables() : outputs).get(b.key()),model.variables().get(e.getKey()));
                } catch (RuntimeException ex) { ExecutionValueDefinitionModel.error(d,"INVALID_BINDING",path+"/outputs/"+e.getKey()); }
            }
        }
    }
}
