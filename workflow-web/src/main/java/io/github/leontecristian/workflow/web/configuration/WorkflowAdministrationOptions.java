package io.github.leontecristian.workflow.web.configuration;

/** Explicit API mount and trusted host request attribute for audit correlation. */
public record WorkflowAdministrationOptions(String basePath, String correlationAttribute) {
    public WorkflowAdministrationOptions {
        if (basePath == null || !basePath.matches("/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*"))
            throw new IllegalArgumentException("Administration requires a non-root literal API base path");
        if (correlationAttribute == null || correlationAttribute.isBlank())
            throw new IllegalArgumentException("Administration requires a trusted correlation attribute");
    }
}
