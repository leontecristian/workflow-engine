package io.github.leontecristian.workflow.testhost.configuration.console;

import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeCommandHost;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeCoordinator;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeReadHost;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeCommandConfiguration;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeOptions;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeReadConfiguration;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowRuntimeCommandHost;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
@Import({WorkflowRuntimeReadConfiguration.class, WorkflowRuntimeCommandConfiguration.class})
public class ConsoleRuntimeConfiguration {
    @Bean WorkflowRuntimeOptions consoleRuntimeOptions() { return new WorkflowRuntimeOptions("/console/api", "console.correlation"); }
    @Bean ConsoleRuntimeReadHost consoleRuntimeReadHost(ConsoleContext context,
            io.github.leontecristian.workflow.testhost.service.runtime.ConsoleTaskReassignmentAuthorization reassignment,
            ConsoleSettings settings) {
        return new ConsoleRuntimeReadHost(context, reassignment, settings);
    }
    @Bean WorkflowRuntimeCommandHost consoleRuntimeCommandHost(ConsoleRuntimeCoordinator commands,
            ConsoleRuntimeTransaction transactions, ConsoleContext context) {
        return new ConsoleRuntimeCommandHost(commands, transactions, context);
    }
}
