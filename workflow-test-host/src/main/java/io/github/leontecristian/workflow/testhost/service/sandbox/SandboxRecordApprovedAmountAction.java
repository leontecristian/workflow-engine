package io.github.leontecristian.workflow.testhost.service.sandbox;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionDescriptor;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowServiceAction;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxServiceEffectStore;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnExpression("'${workflow.console.enabled:false}' == 'true' && '${workflow.console.capabilities:sample}' == 'sample'")
public class SandboxRecordApprovedAmountAction implements WorkflowServiceAction {
    private static final JsonFactory JSON = new JsonFactory();
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000.00");
    private final ObjectProvider<WorkflowRuntimeQueryService> reads;
    private final SandboxServiceEffectStore effects;
    private final ConsoleSettings settings;

    public SandboxRecordApprovedAmountAction(ObjectProvider<WorkflowRuntimeQueryService> reads, SandboxServiceEffectStore effects,
            ConsoleSettings settings) {
        this.reads = reads;
        this.effects = effects;
        this.settings = settings;
    }

    @Override
    public ServiceActionDescriptor descriptor() {
        return new ServiceActionDescriptor("sandbox.recordApprovedAmount", List.of(), List.of());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ServiceActionResult execute(ServiceActionContext context, Map<String, WorkflowValue> inputs) {
        if (context.completedActivityId() == null) throw new IllegalStateException("Missing completed activity");
        // The action registry initializes before the definition-backed runtime reader.
        var page = reads.getObject().listActivitySubmissions(context.completedActivityId(), 0, 2);
        if (page.items().size() != 1 || page.nextAfterTaskId() != null) {
            throw new IllegalStateException("Expected one accepted submission");
        }
        var submission = page.items().get(0);
        if (submission.instanceId() != context.instanceId() || !submission.commandId().equals(context.commandId())) {
            throw new IllegalStateException("Submission does not match the current completion");
        }
        // This action stores approved cents in the existing sandbox effect quantity.
        effects.insert(context, approvedCents(submission.result()));
        if (context.nodeKey().equals(settings.failServiceNode)) throw new IllegalStateException("Configured service failure");
        return new ServiceActionResult(Map.of());
    }

    static long approvedCents(WorkflowJsonValue result) {
        if (result != null) {
            try (var parser = JSON.createParser(result.toJson())) {
                parser.nextToken();
                while (parser.nextToken() == JsonToken.FIELD_NAME) {
                    String name = parser.currentName();
                    var token = parser.nextToken();
                    if ("approvedAmount".equals(name) && token.isNumeric()) {
                        var amount = new BigDecimal(parser.getText()).stripTrailingZeros();
                        if (amount.signum() < 0 || amount.compareTo(MAX_AMOUNT) > 0) break;
                        return amount.movePointRight(2).longValueExact();
                    }
                    parser.skipChildren();
                }
            } catch (IOException failure) {
                throw new IllegalStateException("Accepted submission cannot be decoded", failure);
            }
        }
        throw new IllegalArgumentException("Expected a supported approved amount");
    }
}
