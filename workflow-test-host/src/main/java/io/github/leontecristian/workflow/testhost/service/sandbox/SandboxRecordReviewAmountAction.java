package io.github.leontecristian.workflow.testhost.service.sandbox;

import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionDescriptor;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionResult;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
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
public class SandboxRecordReviewAmountAction implements WorkflowServiceAction {
    private static final String REVIEW_RESULT = "reviewResult";
    private final SandboxServiceEffectStore effects;
    private final ConsoleSettings settings;

    public SandboxRecordReviewAmountAction(SandboxServiceEffectStore effects, ConsoleSettings settings) {
        this.effects = effects;
        this.settings = settings;
    }

    @Override
    public ServiceActionDescriptor descriptor() {
        return new ServiceActionDescriptor("sandbox.recordReviewAmount",
                List.of(new ActionField(REVIEW_RESULT, VariableValueType.JSON, true, false)), List.of());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ServiceActionResult execute(ServiceActionContext context, Map<String, WorkflowValue> inputs) {
        var result = (WorkflowJsonValue) inputs.get(REVIEW_RESULT).value();
        effects.insert(context, SandboxRecordApprovedAmountAction.approvedCents(result));
        if (context.nodeKey().equals(settings.failServiceNode)) throw new IllegalStateException("Configured service failure");
        return new ServiceActionResult(Map.of());
    }
}
