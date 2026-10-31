package io.github.leontecristian.workflow.testhost.exception.console;

/** Marker eligible for one receipt lookup after the failed transaction has rolled back. */
public final class SandboxRequestConflict extends ConsoleException {
    public SandboxRequestConflict() {
        super(409, "SANDBOX_REQUEST_CONFLICT");
    }
}
