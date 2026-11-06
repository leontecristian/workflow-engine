package io.github.leontecristian.workflow.testhost.service.sandbox;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionDescriptor;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionResult;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowServiceAction;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxServiceEffectStore;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnExpression("'${workflow.console.enabled:false}' == 'true' && '${workflow.console.capabilities:sample}' == 'sample'")
public class SandboxRecordQuantityAction implements WorkflowServiceAction {
    private final SandboxServiceEffectStore effects;
    private final ConsoleSettings settings;
    public SandboxRecordQuantityAction(SandboxServiceEffectStore effects,ConsoleSettings settings) { this.effects=effects; this.settings=settings; }
    public ServiceActionDescriptor descriptor() {
        return new ServiceActionDescriptor("sandbox.recordQuantity",List.of(new ActionField("quantity",VariableValueType.LONG,true,false)),
                List.of(new ActionField("recordedQuantity",VariableValueType.LONG,true,false)));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public ServiceActionResult execute(ServiceActionContext context,Map<String,WorkflowValue> inputs) {
        var quantity=inputs.get("quantity");
        effects.insert(context,Long.parseLong(((TextLiteral)quantity.value()).value()));
        if (context.nodeKey().equals(settings.failServiceNode)) throw new IllegalStateException("Configured service failure");
        return new ServiceActionResult(context.nodeKey().equals(settings.invalidServiceOutputNode) ? Map.of() : Map.of("recordedQuantity",quantity));
    }
}
