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
    public final String failServiceNode, invalidServiceOutputNode;
    public final String admin, reviewer, reviewer2, adminPassword, reviewerPassword, reviewer2Password;

    public ConsoleSettings(Environment env) {
        failServiceNode=env.getProperty("workflow.console.fail-service-node", "");
        invalidServiceOutputNode=env.getProperty("workflow.console.invalid-service-output-node", "");
        for(String key:java.util.List.of(failServiceNode,invalidServiceOutputNode))
            if(!key.isEmpty() && !key.matches("[A-Za-z][A-Za-z0-9._-]{0,99}")) throw new IllegalStateException("Invalid service node setting");
        if(!failServiceNode.isEmpty() && failServiceNode.equals(invalidServiceOutputNode)) throw new IllegalStateException("Conflicting failure settings");
        enabled = env.getProperty("workflow.console.enabled", Boolean.class, false);
        String mode = env.getProperty("workflow.console.capabilities", "sample");
        if (!mode.equals("sample") && !mode.equals("none"))
            throw new IllegalStateException("Unknown console capabilities");
        sample = mode.equals("sample");
        admin = env.getProperty("workflow.console.admin.username");
        reviewer = env.getProperty("workflow.console.reviewer.username");
        reviewer2 = env.getProperty("workflow.console.reviewer2.username", reviewer == null ? null : reviewer + "2");
        reviewer2Password = env.getProperty("workflow.console.reviewer2.password", env.getProperty("workflow.console.reviewer.password"));
        adminPassword = env.getProperty("workflow.console.admin.password");
        reviewerPassword = env.getProperty("workflow.console.reviewer.password");
        failAudit = flag(env, "fail-audit");
        failStartLink = flag(env, "fail-start-link");
        failEventRecording = flag(env, "fail-event-recording");
        failRuntimeAfterResult = flag(env, "fail-runtime-after-result");
        if (enabled
                && (!actor(admin)
                        || !actor(reviewer)
                        || !actor(reviewer2)
                        || reviewer2.equals(admin) || reviewer2.equals(reviewer)
                        || reviewer2Password == null || reviewer2Password.isBlank()
                        || admin.equals(reviewer)
                        || adminPassword == null
                        || adminPassword.isBlank()
                        || reviewerPassword == null
                        || reviewerPassword.isBlank()))
            throw new IllegalStateException(
                    "Console requires two distinct accounts and external passwords");
        if (!enabled
                && (failAudit || failStartLink || failEventRecording || failRuntimeAfterResult || !failServiceNode.isEmpty() || !invalidServiceOutputNode.isEmpty()))
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
