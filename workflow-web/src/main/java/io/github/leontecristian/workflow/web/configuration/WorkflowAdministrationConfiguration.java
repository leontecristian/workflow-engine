package io.github.leontecristian.workflow.web.configuration;

import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.web.integration.WorkflowRequestIdentity;
import io.github.leontecristian.workflow.web.integration.administration.WorkflowAdministrationAuthorization;
import io.github.leontecristian.workflow.web.integration.administration.WorkflowAdministrationAuditRecorder;
import io.github.leontecristian.workflow.web.service.administration.WorkflowAdministrationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Clock;
import io.github.leontecristian.workflow.web.controller.tasktype.WorkflowTaskTypeController;
import io.github.leontecristian.workflow.web.controller.definition.WorkflowDefinitionController;
import io.github.leontecristian.workflow.web.service.administration.AdministrationWire;
import io.github.leontecristian.workflow.web.service.administration.AdministrationErrors;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.util.Map;

/** Import explicitly; no stereotype permits activation through scanning. */
public class WorkflowAdministrationConfiguration {
    @Bean public AdministrationWire workflowAdministrationWire() { return new AdministrationWire(); }
    @Bean public AdministrationErrors workflowAdministrationErrors(AdministrationWire wire, WorkflowAdministrationOptions options) {
        return new AdministrationErrors(wire, options);
    }
    @Bean public WorkflowAdministrationService workflowAdministrationService(WorkflowDefinitionService definitions,
            TaskTypeCatalogService catalog, ObjectProvider<WorkflowAdministrationAuditRecorder> audits, Clock clock,
            PlatformTransactionManager transactionManager) {
        // Required dependency ensures activation fails without host transaction infrastructure.
        return new WorkflowAdministrationService(definitions, catalog, audits.getIfAvailable(), clock);
    }
    @Bean public WorkflowTaskTypeController workflowTaskTypeController(TaskTypeCatalogService catalog,
            WorkflowAdministrationService management, WorkflowRequestIdentity identity,
            WorkflowAdministrationAuthorization authorization, AdministrationWire wire, AdministrationErrors errors) {
        return new WorkflowTaskTypeController(catalog, management, identity, authorization, wire, errors);
    }
    @Bean public WorkflowDefinitionController workflowDefinitionController(WorkflowDefinitionService definitions,
            WorkflowAdministrationService management, WorkflowRequestIdentity identity,
            WorkflowAdministrationAuthorization authorization, AdministrationWire wire, AdministrationErrors errors) {
        return new WorkflowDefinitionController(definitions, management, identity, authorization, wire, errors);
    }
    @Bean public RequestMappingHandlerMapping workflowAdministrationHandlerMapping(WorkflowAdministrationOptions options) {
        var mapping = new RequestMappingHandlerMapping() {
            @Override protected boolean isHandler(Class<?> type) { return administrationController(type); }
        };
        mapping.setOrder(-1);
        mapping.setPathPrefixes(Map.of(options.basePath(), WorkflowAdministrationConfiguration::administrationController));
        return mapping;
    }
    private static boolean administrationController(Class<?> type) {
        return type == WorkflowTaskTypeController.class || type == WorkflowDefinitionController.class;
    }
}
