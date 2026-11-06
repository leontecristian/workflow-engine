package io.github.leontecristian.workflow.core.service.runtime;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionDescriptor;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionContext;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import java.util.Map;
/** Host extension. Join the existing transaction; no independently committed or remote effects. */
public interface WorkflowServiceAction {
    ServiceActionDescriptor descriptor();
    ServiceActionResult execute(ServiceActionContext context, Map<String,WorkflowValue> inputs);
}
