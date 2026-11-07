package io.github.leontecristian.workflow.testhost.configuration.console;

import io.github.leontecristian.workflow.web.configuration.WorkflowViewerConfiguration;
import io.github.leontecristian.workflow.web.configuration.WorkflowViewerOptions;
import io.github.leontecristian.workflow.web.integration.instance.WorkflowViewerAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleViewerAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
@Import(WorkflowViewerConfiguration.class)
public class ConsoleViewerConfiguration {
    @Bean WorkflowViewerOptions consoleViewerOptions() { return new WorkflowViewerOptions("/console/api", "console.correlation"); }
    @Bean WorkflowViewerAccess consoleViewerAccess(ConsoleRuntimeAccess access, WorkflowRuntimeQueryService reads) {
        return new ConsoleViewerAccess(access, reads);
    }
}
