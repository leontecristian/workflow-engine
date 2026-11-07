package io.github.leontecristian.workflow.web.exception.instance;

public class WorkflowViewerException extends RuntimeException {
    private final int status;
    private final String code;

    public WorkflowViewerException(int status, String code) {
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

    public static WorkflowViewerException invalid() {
        return new WorkflowViewerException(400, "INVALID_INPUT");
    }

    public static WorkflowViewerException missing() {
        return new WorkflowViewerException(404, "NOT_FOUND");
    }
}
