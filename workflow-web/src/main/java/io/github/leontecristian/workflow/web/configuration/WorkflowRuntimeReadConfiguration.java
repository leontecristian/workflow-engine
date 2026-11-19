package io.github.leontecristian.workflow.web.configuration;

import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.web.controller.runtime.WorkflowRuntimeCommandController;
import io.github.leontecristian.workflow.web.controller.runtime.WorkflowRuntimeReadController;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowAuthorizedRuntimeReadService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeReadAuthorization;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskEligibilityService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskReassignmentAuthorization;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowCancellationAuthorization;
import io.github.leontecristian.workflow.web.integration.WorkflowRequestIdentity;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowAssignmentDirectory;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeErrors;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeWire;
import java.time.Clock;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
/** Plain explicit import: dependency/scanning alone activates no routes. */
public class WorkflowRuntimeReadConfiguration {
    @Bean public WorkflowAuthorizedRuntimeReadService workflowAuthorizedRuntimeReadService(
            WorkflowRuntimeQueryService reads, WorkflowRuntimeReadAuthorization policy,
            WorkflowTaskEligibilityService eligibility,
            ObjectProvider<WorkflowTaskReassignmentAuthorization> reassignment,
            ObjectProvider<WorkflowCancellationAuthorization> cancellation) {
        return new WorkflowAuthorizedRuntimeReadService(reads, policy, eligibility, reassignment, cancellation);
    }
    @Bean public WorkflowRuntimeReadController workflowRuntimeReadController(WorkflowAuthorizedRuntimeReadService readsAccess,
            WorkflowRequestIdentity identity, ObjectProvider<WorkflowAssignmentDirectory> directory,
            ObjectProvider<Clock> clock,
            WorkflowRuntimeOptions options, WorkflowRuntimeQueryService reads, WorkflowDefinitionService definitions,
            TaskTypeCatalogService catalog,
            ObjectProvider<WorkflowRuntimeCommandController> commands) {
        var wire = new RuntimeWire();
        return new WorkflowRuntimeReadController(reads, definitions, catalog, readsAccess, identity,
                directory.getIfAvailable(() -> new WorkflowAssignmentDirectory() {}), clock.getIfAvailable(),
                wire, new RuntimeErrors(wire, options),
                commands.getIfAvailable() != null);
    }
    @Bean public RequestMappingHandlerMapping workflowRuntimeReadHandlerMapping(WorkflowRuntimeOptions options) {
        var mapping = new RequestMappingHandlerMapping() {
            @Override protected boolean isHandler(Class<?> type) { return type == WorkflowRuntimeReadController.class; }
        };
        mapping.setOrder(-1);
        mapping.setPathPrefixes(Map.of(options.basePath(), type -> type == WorkflowRuntimeReadController.class));
        return mapping;
    }
}
