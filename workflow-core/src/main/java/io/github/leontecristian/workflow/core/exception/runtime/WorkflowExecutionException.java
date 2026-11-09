package io.github.leontecristian.workflow.core.exception.runtime;
/** Stable sanitized category; causes are for internal diagnostics, not transport serialization. */
public class WorkflowExecutionException extends RuntimeException {
    public enum Category { INVALID_INPUT, NOT_FOUND, INVALID_STATE, NOT_ASSIGNED, STALE_VERSION,
        COMMAND_ID_CONFLICT, CONCURRENT_COMMAND, INVALID_ASSIGNMENT, UNSUPPORTED_VERSION,
        LIMIT_EXCEEDED, UNINITIALIZED_VARIABLE, INVALID_ACTION_OUTPUT, SERVICE_ACTION_FAILED,
        ROUTE_NOT_FOUND, EXPRESSION_EVALUATION_FAILED, EXPRESSION_LIMIT_EXCEEDED, ASSIGNMENT_LOOKUP_FAILED, AUTHORIZATION_LOOKUP_FAILED,
        TASK_ALREADY_ASSIGNED, TASK_NOT_ASSIGNED, CLAIM_REQUIRED, TASK_NOT_CLAIMABLE,
        TASK_ASSIGNED_TO_OTHER, ADMIN_RELEASE_FORBIDDEN, HOST_CONFIGURATION }
    private final Category category;
    public WorkflowExecutionException(Category category) { this(category, null); }
    public WorkflowExecutionException(Category category, Throwable cause) {
        super("Workflow execution failed: " + category, cause); this.category = category;
    }
    public Category category() { return category; }
}
