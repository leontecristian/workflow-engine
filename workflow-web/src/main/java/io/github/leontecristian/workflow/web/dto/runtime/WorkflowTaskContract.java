package io.github.leontecristian.workflow.web.dto.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.leontecristian.workflow.core.dto.runtime.TaskClaimPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import java.util.List;
import java.util.Map;
/** Contract from the pinned definition and exact historical activity. */
public record WorkflowTaskContract(long taskId, long instanceId, long instanceLockVersion,
        String taskType, List<Outcome> outcomes, boolean canComplete, String assignmentLabel,
        TaskClaimPolicy claimPolicy, boolean canClaim, boolean canRelease, boolean canAdminRelease,
        JsonNode resultContract, Map<String, Input> inputs, PreviousDecisions previousDecisions) {
    public WorkflowTaskContract {
        outcomes = List.copyOf(outcomes);
        inputs = Map.copyOf(inputs);
    }
    public record PreviousDecisions(String mode) {}
    public record Outcome(String key, String label, boolean requiresComment) {}
    public record Input(VariableValueType type, Object value) {}
}
