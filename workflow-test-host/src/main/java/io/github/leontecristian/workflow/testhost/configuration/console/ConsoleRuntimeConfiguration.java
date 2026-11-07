package io.github.leontecristian.workflow.testhost.configuration.console;

import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeCommandHost;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeCoordinator;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeReadHost;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeCommandConfiguration;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeOptions;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeReadConfiguration;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowRuntimeCommandHost;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowRuntimeReadHost;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
@Import({WorkflowRuntimeReadConfiguration.class, WorkflowRuntimeCommandConfiguration.class})
public class ConsoleRuntimeConfiguration {
    @Bean WorkflowRuntimeOptions consoleRuntimeOptions() { return new WorkflowRuntimeOptions("/console/api", "console.correlation"); }
    @Bean WorkflowRuntimeReadHost consoleRuntimeReadHost(WorkflowRuntimeQueryService reads, ConsoleRuntimeAccess access, ConsoleContext context) {
        return new ConsoleRuntimeReadHost(reads, access, context);
    }
    @Bean WorkflowRuntimeCommandHost consoleRuntimeCommandHost(ConsoleRuntimeCoordinator commands,
            ConsoleRuntimeTransaction transactions, ConsoleRuntimeAccess access, ConsoleContext context) {
        return new ConsoleRuntimeCommandHost(commands, transactions, access, context);
    }
}
