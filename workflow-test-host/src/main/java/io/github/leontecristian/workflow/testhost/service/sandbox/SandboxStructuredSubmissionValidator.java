package io.github.leontecristian.workflow.testhost.service.sandbox;

import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmissionValidationContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskSubmissionValidator;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${workflow.console.enabled:false}' == 'true' && '${workflow.console.capabilities:sample}' == 'sample'")
public class SandboxStructuredSubmissionValidator implements WorkflowTaskSubmissionValidator {
    private final WorkflowDefinitionService definitions;
    private final WorkflowRuntimeQueryService reads;
    private final SandboxRequestStore requests;

    public SandboxStructuredSubmissionValidator(WorkflowDefinitionService definitions,
            WorkflowRuntimeQueryService reads, SandboxRequestStore requests) {
        this.definitions = definitions;
        this.reads = reads;
        this.requests = requests;
    }

    @Override
    public void validate(TaskSubmissionValidationContext context, WorkflowJsonValue result) {
        if (!"reviewAmount".equals(context.nodeKey())) return;
        String workflowKey = definitions.findVersion(context.definitionVersionId()).orElseThrow().summary().key();
        if (!"sandbox-structured-review".equals(workflowKey) && !"sandbox-named-task-output".equals(workflowKey)) return;
        var instance = reads.findInstance(context.instanceId()).orElseThrow();
        if (!"SANDBOX_REQUEST".equals(instance.businessType())) throw WorkflowExecutionException.rejectResult("");
        try {
            long requestId = Long.parseLong(instance.businessId());
            var request = requests.find(requestId);
            if (!Long.toString(requestId).equals(instance.businessId())
                    || !Long.valueOf(context.instanceId()).equals(request.instanceId())) {
                throw WorkflowExecutionException.rejectResult("");
            }
        } catch (NumberFormatException | ConsoleException failure) {
            throw WorkflowExecutionException.rejectResult("");
        }
        try {
            SandboxRecordApprovedAmountAction.approvedCents(result);
        } catch (IllegalArgumentException | ArithmeticException failure) {
            throw WorkflowExecutionException.rejectResult("/approvedAmount");
        }
    }
}
