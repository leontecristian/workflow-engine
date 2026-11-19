package io.github.leontecristian.workflow.web.exception;

/** Indicates that the host found no authenticated actor for the current request. */
public final class WorkflowUnauthenticatedException extends RuntimeException {
    public WorkflowUnauthenticatedException(String message) {
        super(message);
    }
}
