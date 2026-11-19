package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.AllAssignmentResult;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentContext;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.CompletionPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.SingleAssignmentResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowEvent;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.function.SingletonSupplier;

/** Optional at bootstrap, mandatory for new commands. Never installs synthetic executable support. */
@Component
public class RuntimeHostSupport {
    private static final Set<AssignmentTargetKind> TARGET_KINDS = Set.of(AssignmentTargetKind.values());

    private record Resolver<T>(T bean, Set<AssignmentTargetKind> kinds) { }

    private final SingletonSupplier<Map<String, Resolver<SingleTaskAssignmentResolver>>> resolvers;
    private final SingletonSupplier<Map<String, Resolver<AllTaskAssignmentResolver>>> allResolvers;
    private final WorkflowTaskEligibilityService eligibility;
    private final List<WorkflowEventBatchWriter> eventBatchWriters;

    public RuntimeHostSupport(ObjectProvider<SingleTaskAssignmentResolver> resolvers,
                              ObjectProvider<WorkflowEventBatchWriter> eventBatchWriters,
                              WorkflowTaskEligibilityService eligibility,
                              ObjectProvider<AllTaskAssignmentResolver> allResolvers) {
        this.resolvers = SingletonSupplier.of(() -> indexResolvers(resolvers,
                SingleTaskAssignmentResolver::identifier, SingleTaskAssignmentResolver::targetKinds));
        this.allResolvers = SingletonSupplier.of(() -> indexResolvers(allResolvers,
                AllTaskAssignmentResolver::identifier, AllTaskAssignmentResolver::targetKinds));
        this.eligibility = eligibility;
        this.eventBatchWriters = eventBatchWriters.stream().toList();
    }

    private static <T> Map<String, Resolver<T>> indexResolvers(ObjectProvider<T> providers,
            Function<T, String> identifier, Function<T, Set<AssignmentTargetKind>> targetKinds) {
        try {
            Map<String, Resolver<T>> registered = new HashMap<>();
            for (T bean : providers.stream().toList()) {
                String id = identifier.apply(bean);
                Set<AssignmentTargetKind> kinds = Set.copyOf(targetKinds.apply(bean));
                if (!RuntimeInputs.isValidText(id, 100, 200, true) || kinds.isEmpty() || !TARGET_KINDS.containsAll(kinds)
                        || registered.putIfAbsent(id, new Resolver<>(bean, kinds)) != null)
                    throw new IllegalArgumentException("Invalid or duplicate resolver descriptor");
            }
            return Map.copyOf(registered);
        } catch (RuntimeException failure) {
            throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure);
        }
    }

    /** Publication uses only metadata and the presence of required shared-target support. */
    public Set<AssignmentTargetKind> assignmentKinds(String id, CompletionPolicy policy) {
        var resolver = registeredResolver(id, policy);
        if (resolver == null) return null;
        eligibility.requireSupport(resolver.kinds());
        return resolver.kinds();
    }

    /** Fresh starts check presence even for stages that may be entered by a later command. */
    public void requireAssignmentResolver(String id, CompletionPolicy policy) {
        RuntimeInputs.require(registeredResolver(id, policy) != null, Category.HOST_CONFIGURATION);
    }

    private Resolver<?> registeredResolver(String id, CompletionPolicy policy) {
        return switch (policy) {
            case SINGLE -> resolvers.obtain().get(id);
            case ALL -> allResolvers.obtain().get(id);
        };
    }

    AllAssignmentResult resolveAll(String id, AssignmentContext context) {
        var resolver = allResolvers.obtain().get(id);
        RuntimeInputs.require(resolver != null, Category.HOST_CONFIGURATION);
        eligibility.requireSupport(resolver.kinds());
        AllAssignmentResult result;
        try {
            result = resolver.bean().resolve(context);
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
                    && resolver.kinds().contains(task.assignment().kind()), Category.INVALID_ASSIGNMENT);
            eligibility.requireTarget(context, task.assignment());
        }
        return result;
    }

    WorkflowEventBatchWriter eventBatchWriter() {
        RuntimeInputs.require(eventBatchWriters.size() == 1, Category.HOST_CONFIGURATION);
        return eventBatchWriters.get(0);
    }

    SingleAssignmentResult resolve(String id, AssignmentContext context) {
        var resolver = resolvers.obtain().get(id);
        RuntimeInputs.require(resolver != null, Category.HOST_CONFIGURATION);
        eligibility.requireSupport(resolver.kinds());
        SingleAssignmentResult result;
        try {
            result = resolver.bean().resolve(context);
        } catch (WorkflowExecutionException failure) {
            if (failure.category() == Category.INVALID_ASSIGNMENT) throw failure;
            throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure);
        } catch (RuntimeException failure) {
            throw RuntimePersistenceErrors.translate(failure);
        }
        RuntimeInputs.require(result != null && result.tasks() != null && result.tasks().size() == 1, Category.INVALID_ASSIGNMENT);
        var task = result.tasks().get(0);
        RuntimeInputs.require(task != null && RuntimeInputs.isValidKey(task.taskKey()) && task.assignment() != null && task.assignment().kind() != null
                && RuntimeInputs.isValidText(task.assignment().ref(), 200, 200, true) && resolver.kinds().contains(task.assignment().kind())
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
