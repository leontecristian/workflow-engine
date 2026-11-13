package io.github.leontecristian.workflow.testhost.service.sandbox;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
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
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records accepted contribution count in the existing transactional sandbox effect store. */
@Component
@ConditionalOnExpression("'${workflow.console.enabled:false}' == 'true' && '${workflow.console.capabilities:sample}' == 'sample'")
public class SandboxRecordReviewCollectionAction implements WorkflowServiceAction {
    private static final JsonFactory JSON = new JsonFactory();
    private final SandboxServiceEffectStore effects;
    private final ConsoleSettings settings;

    public SandboxRecordReviewCollectionAction(SandboxServiceEffectStore effects, ConsoleSettings settings) {
        this.effects = effects;
        this.settings = settings;
    }

    @Override public ServiceActionDescriptor descriptor() {
        return new ServiceActionDescriptor("sandbox.recordReviewCollection",
                List.of(new ActionField("reviews", VariableValueType.JSON, true, false)), List.of());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ServiceActionResult execute(ServiceActionContext context, Map<String, WorkflowValue> inputs) {
        var reviews = (WorkflowJsonValue) inputs.get("reviews").value();
        effects.insert(context, contributionCount(reviews));
        if (context.nodeKey().equals(settings.failServiceNode)) throw new IllegalStateException("Configured service failure");
        return new ServiceActionResult(Map.of());
    }

    static long contributionCount(WorkflowJsonValue reviews) {
        try (var parser = JSON.createParser(reviews.toJson())) {
            if (parser.nextToken() != JsonToken.START_ARRAY) throw new IllegalArgumentException("Expected original review collection");
            int count = 0;
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() != JsonToken.START_OBJECT || ++count > 100) {
                    throw new IllegalArgumentException("Expected bounded review entries");
                }
                parser.skipChildren();
            }
            if (count == 0 || parser.nextToken() != null) throw new IllegalArgumentException("Expected nonempty review collection");
            return count;
        } catch (IOException failure) {
            throw new IllegalStateException("Review collection cannot be decoded", failure);
        }
    }
}
