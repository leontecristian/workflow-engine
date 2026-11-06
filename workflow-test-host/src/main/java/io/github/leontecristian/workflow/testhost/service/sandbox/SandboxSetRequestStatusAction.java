package io.github.leontecristian.workflow.testhost.service.sandbox;

import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionDescriptor;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionResult;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowServiceAction;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Sample business lifecycle; all writes participate in the engine caller's transaction. */
@Component
@ConditionalOnExpression("'${workflow.console.enabled:false}' == 'true' && '${workflow.console.capabilities:sample}' == 'sample'")
public class SandboxSetRequestStatusAction implements WorkflowServiceAction {
    private static final Set<String> STATUSES = Set.of("DRAFT", "IN_REVIEW", "APPROVED", "REJECTED");
    private final SandboxRequestStore requests;
    private final ConsoleSettings settings;

    public SandboxSetRequestStatusAction(SandboxRequestStore requests, ConsoleSettings settings) {
        this.requests = requests;
        this.settings = settings;
    }

    @Override
    public ServiceActionDescriptor descriptor() {
        return new ServiceActionDescriptor("sandbox.setRequestStatus",
                List.of(new ActionField("status", VariableValueType.STRING, true, false)),
                List.of(new ActionField("previousStatus", VariableValueType.STRING, true, false),
                        new ActionField("status", VariableValueType.STRING, true, false)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ServiceActionResult execute(ServiceActionContext context, Map<String, WorkflowValue> inputs) {
        if (!"SANDBOX_REQUEST".equals(context.businessType())) {
            throw new IllegalStateException("Status action requires a sandbox request");
        }
        long requestId;
        try {
            requestId = Long.parseLong(context.businessId());
            if (requestId <= 0 || !Long.toString(requestId).equals(context.businessId())) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException failure) {
            throw new IllegalStateException("Status action requires a valid sandbox request link");
        }
        var value = inputs.get("status");
        if (inputs.size() != 1 || value == null || value.type() != VariableValueType.STRING
                || !(value.value() instanceof TextLiteral text) || !STATUSES.contains(text.value())) {
            throw new IllegalArgumentException("Unsupported sandbox request status");
        }
        String target = text.value();
        var request = requests.find(requestId);
        if (request.instanceId() == null || request.instanceId() != context.instanceId()) {
            throw new IllegalStateException("Sandbox request is not linked to this instance");
        }
        String previous = request.status();
        boolean allowed = STATUSES.contains(previous) && (previous.equals(target)
                || (previous.equals("DRAFT") && target.equals("IN_REVIEW"))
                || (previous.equals("IN_REVIEW") && Set.of("APPROVED", "REJECTED").contains(target)));
        if (!allowed) throw new IllegalStateException("Invalid sandbox request status transition");
        if (!previous.equals(target)) {
            requests.updateStatus(requestId, context.instanceId(), request.lockVersion(), previous, target);
        }
        if (context.nodeKey().equals(settings.failServiceNode)) {
            throw new IllegalStateException("Configured service failure");
        }
        return new ServiceActionResult(context.nodeKey().equals(settings.invalidServiceOutputNode) ? Map.of()
                : Map.of("previousStatus", stringValue(previous), "status", stringValue(target)));
    }

    private static WorkflowValue stringValue(String status) {
        return new WorkflowValue(VariableValueType.STRING, new TextLiteral(status));
    }
}
