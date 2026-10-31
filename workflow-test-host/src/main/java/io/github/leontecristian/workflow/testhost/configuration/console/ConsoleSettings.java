package io.github.leontecristian.workflow.testhost.configuration.console;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ConsoleSettings {
    public final boolean enabled,
            sample,
            failAudit,
            failStartLink,
            failEventRecording,
            failRuntimeAfterResult;
    public final String admin, reviewer, adminPassword, reviewerPassword;

    public ConsoleSettings(Environment env) {
        enabled = env.getProperty("workflow.console.enabled", Boolean.class, false);
        String mode = env.getProperty("workflow.console.capabilities", "sample");
        if (!mode.equals("sample") && !mode.equals("none"))
            throw new IllegalStateException("Unknown console capabilities");
        sample = mode.equals("sample");
        admin = env.getProperty("workflow.console.admin.username");
        reviewer = env.getProperty("workflow.console.reviewer.username");
        adminPassword = env.getProperty("workflow.console.admin.password");
        reviewerPassword = env.getProperty("workflow.console.reviewer.password");
        failAudit = flag(env, "fail-audit");
        failStartLink = flag(env, "fail-start-link");
        failEventRecording = flag(env, "fail-event-recording");
        failRuntimeAfterResult = flag(env, "fail-runtime-after-result");
        if (enabled
                && (!actor(admin)
                        || !actor(reviewer)
                        || admin.equals(reviewer)
                        || adminPassword == null
                        || adminPassword.isBlank()
                        || reviewerPassword == null
                        || reviewerPassword.isBlank()))
            throw new IllegalStateException(
                    "Console requires two distinct accounts and external passwords");
        if (!enabled
                && (failAudit || failStartLink || failEventRecording || failRuntimeAfterResult))
            throw new IllegalStateException(
                    "Console failure switches require the enabled local console");
    }

    private static boolean actor(String value) {
        return io.github.leontecristian.workflow.testhost.service.console.ConsoleText.unicode(value)
                && !value.isBlank()
                && value.length() <= 200;
    }

    private static boolean flag(Environment env, String key) {
        return env.getProperty("workflow.console." + key, Boolean.class, false);
    }
}
