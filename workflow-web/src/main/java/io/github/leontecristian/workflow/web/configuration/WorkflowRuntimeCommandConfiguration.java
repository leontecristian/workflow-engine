package io.github.leontecristian.workflow.web.configuration;

import io.github.leontecristian.workflow.web.controller.runtime.WorkflowRuntimeCommandController;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowExecutionService;
import io.github.leontecristian.workflow.web.integration.WorkflowRequestIdentity;
import io.github.leontecristian.workflow.web.service.runtime.WorkflowRuntimeCommandCoordinator;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeErrors;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeWire;
import java.time.Clock;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
/** Plain explicit import: dependency/scanning alone activates no routes. */
public class WorkflowRuntimeCommandConfiguration {
    @Bean public WorkflowRuntimeCommandController workflowRuntimeCommandController(
            WorkflowRuntimeCommandCoordinator coordinator,
            WorkflowRuntimeOptions options) {
        var wire = new RuntimeWire();
        return new WorkflowRuntimeCommandController(coordinator, wire, new RuntimeErrors(wire, options));
    }
    @Bean public WorkflowRuntimeCommandCoordinator workflowRuntimeCommandCoordinator(WorkflowRequestIdentity identity,
            Clock clock, WorkflowExecutionService execution, PlatformTransactionManager transactionManager) {
        return new WorkflowRuntimeCommandCoordinator(identity, clock, execution, transactionManager);
    }

    @Bean public RequestMappingHandlerMapping workflowRuntimeCommandHandlerMapping(WorkflowRuntimeOptions options) {
        var mapping = new RequestMappingHandlerMapping() {
            @Override protected boolean isHandler(Class<?> type) { return type == WorkflowRuntimeCommandController.class; }
        };
        mapping.setOrder(-1);
        mapping.setPathPrefixes(Map.of(options.basePath(), type -> type == WorkflowRuntimeCommandController.class));
        return mapping;
    }
}
