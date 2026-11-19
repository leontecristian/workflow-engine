package io.github.leontecristian.workflow.testhost.configuration.console;

import io.github.leontecristian.workflow.testhost.service.console.ConsoleRuntimeConflictResolver;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeReadHost;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleTaskReassignmentAuthorization;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeCommandConfiguration;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeOptions;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeReadConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
@Import({WorkflowRuntimeReadConfiguration.class, WorkflowRuntimeCommandConfiguration.class})
public class ConsoleRuntimeConfiguration {
    @Bean WorkflowRuntimeOptions consoleRuntimeOptions() { return new WorkflowRuntimeOptions("/console/api", "console.correlation"); }
    @Bean ConsoleRuntimeReadHost consoleRuntimeReadHost(ConsoleTaskReassignmentAuthorization reassignment,
            ConsoleSettings settings) {
        return new ConsoleRuntimeReadHost(reassignment, settings);
    }
    @Bean ConsoleRuntimeConflictResolver consoleRuntimeConflictResolver(WorkflowRuntimeOptions options) {
        return new ConsoleRuntimeConflictResolver(options);
    }
}
