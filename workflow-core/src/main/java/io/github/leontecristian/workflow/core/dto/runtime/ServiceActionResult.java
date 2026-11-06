package io.github.leontecristian.workflow.core.dto.runtime;
import java.util.Map;
public record ServiceActionResult(Map<String, WorkflowValue> outputs) {
    public ServiceActionResult {
        if (outputs==null || outputs.size()>100) throw new IllegalArgumentException("Invalid outputs");
        outputs=Map.copyOf(outputs);
    }
}
