package io.github.leontecristian.workflow.core.dto.runtime;
import java.util.List;
public record WorkflowVariablePage(long instanceId, long lockVersion, List<WorkflowVariableView> items, String nextAfterKey) {
    public WorkflowVariablePage { items=List.copyOf(items); }
}
