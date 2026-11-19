package io.github.leontecristian.workflow.testhost.configuration.console;

import io.github.leontecristian.workflow.web.configuration.WorkflowViewerConfiguration;
import io.github.leontecristian.workflow.web.configuration.WorkflowViewerOptions;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleBusinessHistory;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleViewerAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeReadHost;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
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
    @Bean ConsoleViewerAccess consoleViewerAccess(ConsoleRuntimeAccess access, WorkflowRuntimeQueryService reads,
            ConsoleContext identity) {
        return new ConsoleViewerAccess(access, reads, identity);
    }
    @Bean ConsoleBusinessHistory consoleBusinessHistory(ConsoleRuntimeAccess access, ConsoleViewerAccess viewer,
            WorkflowRuntimeQueryService reads, WorkflowDefinitionService definitions, ConsoleRuntimeReadHost directory,
            ConsoleWire wire) {
        return new ConsoleBusinessHistory(access, viewer, reads, definitions, directory, wire);
    }
}
