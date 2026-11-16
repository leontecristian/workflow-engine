package io.github.leontecristian.workflow.testhost.configuration.console;

import io.github.leontecristian.workflow.web.configuration.WorkflowAdministrationConfiguration;
import io.github.leontecristian.workflow.web.configuration.WorkflowAdministrationOptions;
import io.github.leontecristian.workflow.testhost.repository.audit.ManagementAuditStore;
import io.github.leontecristian.workflow.testhost.service.administration.ConsoleAdministrationHost;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
@Import(WorkflowAdministrationConfiguration.class)
public class ConsoleAdministrationConfiguration {
    @Bean WorkflowAdministrationOptions consoleAdministrationOptions() {
        return new WorkflowAdministrationOptions("/console/api", "console.correlation");
    }
    @Bean ConsoleAdministrationHost consoleAdministrationHost(ManagementAuditStore audits, ConsoleWire wire, ConsoleSettings settings) {
        return new ConsoleAdministrationHost(audits, wire, settings);
    }
}
