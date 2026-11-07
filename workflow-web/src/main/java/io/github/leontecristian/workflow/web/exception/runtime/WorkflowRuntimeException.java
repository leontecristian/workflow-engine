package io.github.leontecristian.workflow.web.exception.runtime;

/** Scoped HTTP/policy error, with no private details in its message. */
public class WorkflowRuntimeException extends RuntimeException {
    private final int status;
    private final String code;
    public WorkflowRuntimeException(int status, String code) { super(code); this.status = status; this.code = code; }
    public int status() { return status; }
    public String code() { return code; }
    public static WorkflowRuntimeException invalid() { return new WorkflowRuntimeException(400, "INVALID_INPUT"); }
}
