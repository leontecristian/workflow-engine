package io.github.leontecristian.workflow.core.exception.runtime;
/** Stable sanitized category; causes are for internal diagnostics, not transport serialization. */
public class WorkflowExecutionException extends RuntimeException {
    public enum Category { INVALID_INPUT, NOT_FOUND, INVALID_STATE, NOT_ASSIGNED, STALE_VERSION,
        CONFLICT, COMMAND_ID_CONFLICT, CONCURRENT_COMMAND, INVALID_ASSIGNMENT, UNSUPPORTED_VERSION,
        LIMIT_EXCEEDED, UNINITIALIZED_VARIABLE, SUBMISSION_UNAVAILABLE, INVALID_ACTION_OUTPUT, SERVICE_ACTION_FAILED,
        ROUTE_NOT_FOUND, EXPRESSION_EVALUATION_FAILED, EXPRESSION_LIMIT_EXCEEDED, ASSIGNMENT_LOOKUP_FAILED, AUTHORIZATION_LOOKUP_FAILED,
        TASK_ALREADY_ASSIGNED, TASK_NOT_ASSIGNED, CLAIM_REQUIRED, TASK_NOT_CLAIMABLE,
        TASK_ASSIGNED_TO_OTHER, ADMIN_RELEASE_FORBIDDEN, RESULT_SCHEMA_INVALID, RESULT_REJECTED, RESULT_VALIDATION_FAILED, HOST_CONFIGURATION }
    public record Diagnostic(String path, String schemaPath) {}
    private final Category category;
    private final Diagnostic diagnostic;
    private final String safeMessage;
    public WorkflowExecutionException(Category category) { this(category, null); }
    public WorkflowExecutionException(Category category, Throwable cause) {
        this(category, cause, null, null);
    }
    private WorkflowExecutionException(Category category, Throwable cause, Diagnostic diagnostic, String safeMessage) {
        super("Workflow execution failed: " + category, cause);
        this.category = category;
        this.diagnostic = diagnostic;
        this.safeMessage = safeMessage;
    }
    /** The path identifies a field and must not contain any rejected value. */
    public static WorkflowExecutionException rejectResult(String path) {
        return new WorkflowExecutionException(Category.RESULT_REJECTED, null, new Diagnostic(path, null), null);
    }
    public static WorkflowExecutionException invalidResultSchema(String path, String schemaPath) {
        return new WorkflowExecutionException(Category.RESULT_SCHEMA_INVALID, null, new Diagnostic(path, schemaPath), null);
    }
    /** Use only an engine-owned message describing the limit, with no input values. */
    public static WorkflowExecutionException resultLimit(String trustedMessage) {
        return new WorkflowExecutionException(Category.LIMIT_EXCEEDED, null, null, trustedMessage);
    }
    public String safeMessage() { return safeMessage; }
    public Category category() { return category; }
    public Diagnostic diagnostic() { return diagnostic; }
}
