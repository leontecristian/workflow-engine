package io.github.leontecristian.workflow.core.exception.runtime;

/** A denied collection grant, distinct from concealed exact-resource NOT_FOUND. */
public class WorkflowRuntimeReadAccessException extends RuntimeException {
    public enum Access { MONITOR, ADMINISTRATION }

    private final Access access;

    public WorkflowRuntimeReadAccessException(Access access) {
        super("Workflow collection access denied: " + access);
        this.access = access;
    }

    public Access access() { return access; }
}
