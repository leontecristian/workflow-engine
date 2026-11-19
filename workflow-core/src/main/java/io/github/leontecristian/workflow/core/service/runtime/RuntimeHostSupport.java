package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AllAssignmentResult;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentContext;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.SingleAssignmentResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowEvent;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.leontecristian.workflow.core.service.definition.DefinitionCapabilityProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Optional at bootstrap, mandatory for new commands. Never installs synthetic executable support. */
@Component
public class RuntimeHostSupport {
    private final Map<String, SingleTaskAssignmentResolver> resolvers;
    private Map<String, AllTaskAssignmentResolver> allResolvers = Map.of();
    private RuntimeException allResolverConfigurationFailure;
    private final WorkflowTaskEligibilityService eligibility;
    private final DefinitionCapabilityProvider capabilities;
    private final List<WorkflowEventBatchWriter> eventBatchWriters;
    private final RuntimeException resolverConfigurationFailure;

    public RuntimeHostSupport(ObjectProvider<SingleTaskAssignmentResolver> resolvers,
                              ObjectProvider<WorkflowEventBatchWriter> eventBatchWriters,
                              WorkflowTaskEligibilityService eligibility,
                              ObjectProvider<DefinitionCapabilityProvider> capabilities) {
        this.eligibility = eligibility;
        var descriptors = capabilities.stream().toList();
        this.capabilities = descriptors.size() == 1 ? descriptors.get(0) : null;
        Map<String, SingleTaskAssignmentResolver> registered = new HashMap<>();
        RuntimeException error = null;
        try {
            for (var resolver : resolvers.stream().toList()) {
                String id = resolver.identifier();
                if (!RuntimeInputs.isValidText(id, 100, 200, true) || registered.putIfAbsent(id, resolver) != null)
                    throw new IllegalArgumentException("Invalid or duplicate resolver identifier");
            }
        } catch (RuntimeException failure) {
            error = failure;
        }
        this.resolvers = Map.copyOf(registered);
        this.resolverConfigurationFailure = error;
        this.eventBatchWriters = eventBatchWriters.stream().toList();
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RuntimeHostSupport(ObjectProvider<SingleTaskAssignmentResolver> resolvers,
                              ObjectProvider<WorkflowEventBatchWriter> eventBatchWriters,
                              WorkflowTaskEligibilityService eligibility,
                              ObjectProvider<DefinitionCapabilityProvider> capabilities,
                              ObjectProvider<AllTaskAssignmentResolver> allResolvers) {
        this(resolvers, eventBatchWriters, eligibility, capabilities);
        var registered = new HashMap<String, AllTaskAssignmentResolver>();
        try {
            for (var resolver : allResolvers.stream().toList()) {
                String id = resolver.identifier();
                RuntimeInputs.require(RuntimeInputs.isValidText(id, 100, 200, true) && registered.putIfAbsent(id, resolver) == null,
                        Category.HOST_CONFIGURATION);
            }
            this.allResolvers = Map.copyOf(registered);
        } catch (RuntimeException failure) {
            allResolverConfigurationFailure = failure;
        }
    }

    public void requireAllResolver(String id,
                                   java.util.Set<AssignmentTargetKind> kinds) {
        RuntimeInputs.require(allResolverConfigurationFailure == null, Category.HOST_CONFIGURATION);
        var resolver = allResolvers.get(id);
        RuntimeInputs.require(resolver != null, Category.HOST_CONFIGURATION);
        try {
            RuntimeInputs.require(kinds != null && !kinds.isEmpty() && kinds.equals(resolver.targetKinds()), Category.HOST_CONFIGURATION);
            eligibility.requireSupport(kinds);
        } catch (WorkflowExecutionException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure);
        }
    }

    AllAssignmentResult resolveAll(String id, AssignmentContext context) {
        RuntimeInputs.require(capabilities != null, Category.HOST_CONFIGURATION);
        try {
            var descriptor = capabilities.snapshot().allAssignments().stream().filter(c -> c.resolver().equals(id)).findFirst()
                    .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
            requireAllResolver(id, descriptor.targetKinds());
        } catch (WorkflowExecutionException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure);
        }
        var resolver = allResolvers.get(id);
        AllAssignmentResult result;
        try {
            result = resolver.resolve(context);
        } catch (WorkflowExecutionException failure) {
            if (failure.category() == Category.INVALID_ASSIGNMENT) throw failure;
            throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure);
        } catch (RuntimeException failure) {
            throw RuntimePersistenceErrors.translate(failure);
        }
        RuntimeInputs.require(result != null && result.tasks() != null && result.tasks().size() >= 1 && result.tasks().size() <= 100
                && (result.reviewRevisionRef() == null || RuntimeInputs.isValidText(result.reviewRevisionRef(), 200, 200, true)), Category.INVALID_ASSIGNMENT);
        var keys = new java.util.HashSet<String>();
        for (var task : result.tasks()) {
            RuntimeInputs.require(task != null && RuntimeInputs.isValidKey(task.taskKey()) && keys.add(task.taskKey())
                    && task.assignment() != null && task.assignment().kind() != null
                    && RuntimeInputs.isValidText(task.assignment().ref(), 200, 200, true)
                    && resolver.targetKinds().contains(task.assignment().kind()), Category.INVALID_ASSIGNMENT);
            eligibility.requireTarget(context, task.assignment());
        }
        return result;
    }

    WorkflowEventBatchWriter eventBatchWriter() {
        RuntimeInputs.require(eventBatchWriters.size() == 1, Category.HOST_CONFIGURATION);
        return eventBatchWriters.get(0);
    }

    SingleTaskAssignmentResolver resolver(String id) {
        if (resolverConfigurationFailure != null)
            throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, resolverConfigurationFailure);
        SingleTaskAssignmentResolver resolver = resolvers.get(id);
        RuntimeInputs.require(resolver != null, Category.HOST_CONFIGURATION);
        return resolver;
    }

    public void requireResolver(String id, java.util.Set<AssignmentTargetKind> kinds) {
        var resolver = resolver(id);
        try {
            RuntimeInputs.require(kinds != null && kinds.equals(resolver.targetKinds()), Category.HOST_CONFIGURATION);
            eligibility.requireSupport(kinds);
        } catch (WorkflowExecutionException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure);
        }
    }

    SingleAssignmentResult resolve(SingleTaskAssignmentResolver resolver, AssignmentContext context) {
        RuntimeInputs.require(capabilities != null, Category.HOST_CONFIGURATION);
        try {
            var descriptor = capabilities.snapshot().assignments().stream().filter(c -> c.resolver().equals(resolver.identifier())).findFirst()
                    .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
            requireResolver(resolver.identifier(), descriptor.targetKinds());
        } catch (WorkflowExecutionException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure);
        }
        SingleAssignmentResult result;
        try {
            result = resolver.resolve(context);
        } catch (WorkflowExecutionException failure) {
            if (failure.category() == Category.INVALID_ASSIGNMENT) throw failure;
            throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure);
        } catch (RuntimeException failure) {
            throw RuntimePersistenceErrors.translate(failure);
        }
        RuntimeInputs.require(result != null && result.tasks() != null && result.tasks().size() == 1, Category.INVALID_ASSIGNMENT);
        var task = result.tasks().get(0);
        RuntimeInputs.require(task != null && RuntimeInputs.isValidKey(task.taskKey()) && task.assignment() != null && task.assignment().kind() != null
                && RuntimeInputs.isValidText(task.assignment().ref(), 200, 200, true) && resolver.targetKinds().contains(task.assignment().kind())
                && (result.reviewRevisionRef() == null || RuntimeInputs.isValidText(result.reviewRevisionRef(), 200, 200, true)), Category.INVALID_ASSIGNMENT);
        eligibility.requireTarget(context, task.assignment());
        return result;
    }

    void writeEvents(WorkflowEventBatchWriter writer, List<WorkflowEvent> events) {
        try {
            writer.write(List.copyOf(events));
        } catch (RuntimeException failure) {
            throw RuntimePersistenceErrors.translate(failure);
        }
    }
}
