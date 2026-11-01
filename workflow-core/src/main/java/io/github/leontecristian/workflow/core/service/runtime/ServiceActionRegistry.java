package io.github.leontecristian.workflow.core.service.runtime;
import io.github.leontecristian.workflow.core.dto.runtime.ServiceActionDescriptor;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
/** Process snapshot shared by publication and execution. */
@Component
public class ServiceActionRegistry {
    private record Registration(ServiceActionDescriptor descriptor, WorkflowServiceAction action) {}
    private final Map<String,Registration> actions;
    public ServiceActionRegistry(ObjectProvider<WorkflowServiceAction> provider) {
        try {
            Map<String,Registration> found=new TreeMap<>();
            var iterator=provider.iterator();
            while (iterator.hasNext()) {
                if (found.size()>=256) throw new IllegalArgumentException("Too many actions");
                var action=iterator.next(); var d=action.descriptor();
                if (d==null || found.putIfAbsent(d.identifier(),new Registration(d,action))!=null) throw new IllegalArgumentException("Invalid registrations");
            }
            actions=Map.copyOf(found);
        } catch (RuntimeException e) { throw new WorkflowExecutionException(Category.HOST_CONFIGURATION,e); }
    }
    public ServiceActionDescriptor descriptor(String identifier) { var r=actions.get(identifier); return r==null ? null : r.descriptor(); }
    WorkflowServiceAction action(String identifier) {
        var r=actions.get(identifier);
        if (r==null) throw new WorkflowExecutionException(Category.HOST_CONFIGURATION);
        return r.action();
    }
}
