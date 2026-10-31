package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentContext;
import io.github.leontecristian.workflow.core.dto.runtime.SingleAssignmentResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowEvent;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Optional at bootstrap, mandatory for new commands. Never installs synthetic executable support. */
@Component
public class RuntimeHostSupport {
    private final Map<String, SingleTaskAssignmentResolver> resolvers;
    private final List<WorkflowEventRecorder> recorders;
    private final RuntimeException resolverConfigurationFailure;
    public RuntimeHostSupport(ObjectProvider<SingleTaskAssignmentResolver> resolvers, ObjectProvider<WorkflowEventRecorder> recorders) {
        Map<String, SingleTaskAssignmentResolver> registered = new HashMap<>();
        RuntimeException error = null;
        try {
            for (var resolver : resolvers.stream().toList()) {
                String id = resolver.identifier();
                if (!RuntimeInputs.text(id, 100, 200, true) || registered.putIfAbsent(id, resolver) != null)
                    throw new IllegalArgumentException("Invalid or duplicate resolver identifier");
            }
        } catch (RuntimeException failure) { error = failure; }
        this.resolvers = Map.copyOf(registered); this.resolverConfigurationFailure = error;
        this.recorders = recorders.stream().toList();
    }
    WorkflowEventRecorder recorder() {
        RuntimeInputs.require(recorders.size() == 1, Category.HOST_CONFIGURATION); return recorders.get(0);
    }
    SingleTaskAssignmentResolver resolver(String id) {
        if (resolverConfigurationFailure != null) throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, resolverConfigurationFailure);
        SingleTaskAssignmentResolver resolver = resolvers.get(id);
        RuntimeInputs.require(resolver != null, Category.HOST_CONFIGURATION); return resolver;
    }
    SingleAssignmentResult resolve(SingleTaskAssignmentResolver resolver, AssignmentContext context) {
        SingleAssignmentResult result;
        try { result = resolver.resolve(context); }
        catch (WorkflowExecutionException failure) {
            if (failure.category() == Category.INVALID_ASSIGNMENT) throw failure;
            throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure);
        } catch (RuntimeException failure) { throw RuntimePersistenceErrors.translate(failure); }
        RuntimeInputs.require(result != null && result.tasks() != null && result.tasks().size() == 1, Category.INVALID_ASSIGNMENT);
        var task = result.tasks().get(0);
        RuntimeInputs.require(task != null && RuntimeInputs.key(task.taskKey()) && RuntimeInputs.text(task.assignedUserRef(), 200, 200, true)
                && (result.reviewRevisionRef() == null || RuntimeInputs.text(result.reviewRevisionRef(), 200, 200, true)), Category.INVALID_ASSIGNMENT);
        return result;
    }
    void record(WorkflowEventRecorder recorder, List<WorkflowEvent> events) {
        try { recorder.record(List.copyOf(events)); }
        catch (RuntimeException failure) { throw RuntimePersistenceErrors.translate(failure); }
    }
}
