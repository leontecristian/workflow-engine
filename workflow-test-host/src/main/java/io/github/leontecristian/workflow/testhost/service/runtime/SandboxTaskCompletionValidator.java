package io.github.leontecristian.workflow.testhost.service.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leontecristian.workflow.core.dto.runtime.TaskCompletionContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.exception.runtime.TaskCompletionRejectedException;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskCompletionValidator;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import org.springframework.stereotype.Component;

/** Local stored baseline fixture only; no production booking rules or effects. */
@Component
public class SandboxTaskCompletionValidator implements WorkflowTaskCompletionValidator {
    private final SandboxRequestStore requests;
    private final ObjectMapper json;

    public SandboxTaskCompletionValidator(SandboxRequestStore requests, ObjectMapper json) {
        this.requests = requests;
        this.json = json;
    }

    @Override public void validate(TaskCompletionContext context, WorkflowJsonValue result) {
        var request = requests.lockApprovalBasis(context.task().instanceId());
        if (request.requestDataJson() == null) return;
        try {
            var fixture = json.readTree(request.requestDataJson()).path("completionFixture");
            if (fixture.isMissingNode()) return;
            if (!fixture.isObject() || !fixture.has("approvalBasis") || !fixture.has("currentBasis")
                    || !fixture.get("approvalBasis").equals(fixture.get("currentBasis"))) {
                throw new TaskCompletionRejectedException();
            }
        } catch (JsonProcessingException malformed) {
            throw new IllegalStateException("Invalid stored completion fixture", malformed);
        }
    }
}
