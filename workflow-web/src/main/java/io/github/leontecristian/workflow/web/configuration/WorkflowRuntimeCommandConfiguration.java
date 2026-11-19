package io.github.leontecristian.workflow.web.configuration;

import io.github.leontecristian.workflow.web.controller.runtime.WorkflowRuntimeCommandController;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowExecutionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowAuthorizedRuntimeReadService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.web.integration.WorkflowRequestIdentity;
import io.github.leontecristian.workflow.web.service.runtime.WorkflowRuntimeCommandCoordinator;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeErrors;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeWire;
import io.github.leontecristian.workflow.web.service.runtime.WorkflowTaskTokens;
import java.time.Clock;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
/** Plain explicit import: dependency/scanning alone activates no routes. */
@Import(WorkflowTaskTokenConfiguration.class)
public class WorkflowRuntimeCommandConfiguration {
    @Bean public WorkflowRuntimeCommandController workflowRuntimeCommandController(
            WorkflowRuntimeCommandCoordinator coordinator,
            WorkflowRuntimeOptions options) {
        var wire = new RuntimeWire();
        return new WorkflowRuntimeCommandController(coordinator, wire, new RuntimeErrors(wire, options));
    }
    @Bean public WorkflowRuntimeCommandCoordinator workflowRuntimeCommandCoordinator(WorkflowRequestIdentity identity,
            Clock clock, WorkflowExecutionService execution, PlatformTransactionManager transactionManager,
            ObjectProvider<WorkflowRuntimeQueryService> reads, ObjectProvider<WorkflowAuthorizedRuntimeReadService> readAccess,
            WorkflowTaskTokens tokens) {
        return new WorkflowRuntimeCommandCoordinator(identity, clock, execution, transactionManager,
                reads.getIfAvailable(), readAccess::getIfAvailable, tokens);
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
