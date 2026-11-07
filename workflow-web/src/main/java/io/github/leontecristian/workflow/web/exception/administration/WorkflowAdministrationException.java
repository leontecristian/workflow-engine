package io.github.leontecristian.workflow.web.exception.administration;

public class WorkflowAdministrationException extends RuntimeException {
    private final int status;
    private final String code;

    public WorkflowAdministrationException(int status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static WorkflowAdministrationException invalid() {
        return new WorkflowAdministrationException(400, "INVALID_INPUT");
    }

    public static WorkflowAdministrationException missing() {
        return new WorkflowAdministrationException(404, "NOT_FOUND");
    }
}
