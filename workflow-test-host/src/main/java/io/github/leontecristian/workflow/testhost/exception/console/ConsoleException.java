package io.github.leontecristian.workflow.testhost.exception.console;

public class ConsoleException extends RuntimeException {
    private final int status;
    private final String code;

    public ConsoleException(int status, String code) {
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

    public static ConsoleException invalid() {
        return new ConsoleException(400, "INVALID_INPUT");
    }

    public static ConsoleException missing() {
        return new ConsoleException(404, "NOT_FOUND");
    }
}
