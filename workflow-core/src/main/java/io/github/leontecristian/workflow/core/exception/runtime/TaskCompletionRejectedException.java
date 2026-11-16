package io.github.leontecristian.workflow.core.exception.runtime;

/** Explicit host business rejection. No free-text diagnostic or protected material crosses the boundary. */
public final class TaskCompletionRejectedException extends RuntimeException {
    public TaskCompletionRejectedException() { super("Task completion rejected"); }
}
