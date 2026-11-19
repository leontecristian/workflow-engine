package io.github.leontecristian.workflow.web.configuration;

import io.github.leontecristian.workflow.web.service.runtime.WorkflowTaskTokens;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

class WorkflowTaskTokenConfiguration {
    @Bean WorkflowTaskTokens workflowTaskTokens(Environment environment) {
        return new WorkflowTaskTokens(environment.getProperty("workflow.runtime.task-token-key"));
    }
}
