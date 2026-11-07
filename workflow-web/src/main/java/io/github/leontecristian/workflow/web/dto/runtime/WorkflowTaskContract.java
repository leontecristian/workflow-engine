package io.github.leontecristian.workflow.web.dto.runtime;

import java.util.List;
/** Contract from the pinned definition and exact historical activity. */
public record WorkflowTaskContract(long taskId, long instanceId, long instanceLockVersion,
        String taskType, List<Outcome> outcomes, boolean canComplete, String assignmentLabel) {
    public WorkflowTaskContract { outcomes = List.copyOf(outcomes); }
    public record Outcome(String key, String label, boolean requiresComment) {}
}
