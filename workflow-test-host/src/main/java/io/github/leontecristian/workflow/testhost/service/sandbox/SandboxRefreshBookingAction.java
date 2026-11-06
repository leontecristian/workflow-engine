package io.github.leontecristian.workflow.testhost.service.sandbox;

import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionDescriptor;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionResult;
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

/** Explicit sample replacement; the effect marker participates in the caller's transaction. */
@Component
@ConditionalOnExpression("'${workflow.console.enabled:false}' == 'true' && '${workflow.console.capabilities:sample}' == 'sample'")
public class SandboxRefreshBookingAction implements WorkflowServiceAction {
    private final SandboxServiceEffectStore effects;
    private final ConsoleSettings settings;
    public SandboxRefreshBookingAction(SandboxServiceEffectStore effects, ConsoleSettings settings) {
        this.effects=effects; this.settings=settings;
    }
    public ServiceActionDescriptor descriptor() {
        var field=new ActionField("booking",VariableValueType.JSON,true,false);
        return new ServiceActionDescriptor("sandbox.refreshBooking",List.of(field),List.of(field));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public ServiceActionResult execute(ServiceActionContext context,Map<String,WorkflowValue> inputs) {
        effects.insert(context,1);
        if (context.nodeKey().equals(settings.failServiceNode)) throw new IllegalStateException("Configured service failure");
        var replacement=WorkflowJsonValue.parse("{\"amount\":12500.00,\"department\":{\"code\":\"SALES\"}}");
        return new ServiceActionResult(context.nodeKey().equals(settings.invalidServiceOutputNode) ? Map.of()
                : Map.of("booking",new WorkflowValue(VariableValueType.JSON,replacement)));
    }
}
