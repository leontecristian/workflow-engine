package io.github.leontecristian.workflow.web.configuration;

/** Explicit API mount and trusted host correlation attribute for either runtime group. */
public record WorkflowRuntimeOptions(String basePath, String correlationAttribute) {
    public WorkflowRuntimeOptions {
        if (basePath == null || !basePath.matches("/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*"))
            throw new IllegalArgumentException("Runtime requires a non-root literal API base path");
        if (correlationAttribute == null || correlationAttribute.isBlank())
            throw new IllegalArgumentException("Runtime requires a trusted correlation attribute");
    }
}
