package io.github.leontecristian.workflow.web.configuration;

/** One explicit mount. Correlation comes only from a trusted host request attribute. */
public record WorkflowViewerOptions(String basePath, String correlationAttribute) {
    public WorkflowViewerOptions {
        if (basePath == null || !basePath.matches("/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*"))
            throw new IllegalArgumentException("Workflow viewer requires a nonempty, non-root literal API base path");
    }
}
