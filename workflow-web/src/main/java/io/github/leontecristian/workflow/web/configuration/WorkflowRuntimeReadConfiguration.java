package io.github.leontecristian.workflow.web.configuration;

import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.web.controller.runtime.WorkflowRuntimeReadController;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowRuntimeReadHost;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeErrors;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeWire;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
/** Plain explicit import: dependency/scanning alone activates no routes. */
public class WorkflowRuntimeReadConfiguration {
    @Bean public WorkflowRuntimeReadController workflowRuntimeReadController(WorkflowRuntimeReadHost host,
            WorkflowRuntimeOptions options, WorkflowRuntimeQueryService reads, WorkflowDefinitionService definitions) {
        var wire = new RuntimeWire();
        return new WorkflowRuntimeReadController(reads, definitions, host, wire, new RuntimeErrors(wire, options));
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
