package io.github.leontecristian.workflow.core;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit entry point for workflow components. The host supplies persistence
 * infrastructure, entity/repository scanning and migration execution.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = WorkflowConfiguration.class)
public class WorkflowConfiguration {
}
