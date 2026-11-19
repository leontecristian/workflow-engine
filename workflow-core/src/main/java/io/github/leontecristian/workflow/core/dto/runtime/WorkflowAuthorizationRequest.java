package io.github.leontecristian.workflow.core.dto.runtime;

import java.util.Objects;

/** A policy decision for an exact verified resource and a trusted actor. */
public sealed interface WorkflowAuthorizationRequest {
    WorkflowPermission permission();

    String actorRef();

    long instanceId();

    record Task(WorkflowPermission permission, TaskEligibilityContext task) implements WorkflowAuthorizationRequest {
        public Task {
            Objects.requireNonNull(task, "task context");
            Objects.requireNonNull(task.task(), "task");
            Objects.requireNonNull(task.actorRef(), "actor reference");
            if (permission.resource() != WorkflowResource.TASK || permission.scope() == WorkflowScope.COMPLETE) {
                throw new IllegalArgumentException("Permission requires a different workflow authorization context");
            }
        }

        @Override
        public String actorRef() {
            return task.actorRef();
        }

        @Override
        public long instanceId() {
            return task.task().instanceId();
        }
    }

    record Completion(WorkflowPermission permission, TaskCompletionContext task) implements WorkflowAuthorizationRequest {
        public Completion {
            Objects.requireNonNull(task, "completion context");
            Objects.requireNonNull(task.task(), "task");
            Objects.requireNonNull(task.commandContext(), "command context");
            Objects.requireNonNull(task.commandContext().actorRef(), "actor reference");
            if (permission.scope() != WorkflowScope.COMPLETE) {
                throw new IllegalArgumentException("Permission requires a different workflow authorization context");
            }
        }

        @Override
        public String actorRef() {
            return task.commandContext().actorRef();
        }

        @Override
        public long instanceId() {
            return task.task().instanceId();
        }
    }

    record Cancellation(WorkflowPermission permission, WorkflowCancellationAuthorizationContext cancellation)
            implements WorkflowAuthorizationRequest {
        public Cancellation {
            Objects.requireNonNull(cancellation, "cancellation context");
            Objects.requireNonNull(cancellation.commandContext(), "command context");
            Objects.requireNonNull(cancellation.commandContext().actorRef(), "actor reference");
            if (permission.resource() != WorkflowResource.INSTANCE) {
                throw new IllegalArgumentException("Permission requires a different workflow authorization context");
            }
        }

        @Override
        public String actorRef() {
            return cancellation.commandContext().actorRef();
        }

        @Override
        public long instanceId() {
            return cancellation.instanceId();
        }
    }
}
