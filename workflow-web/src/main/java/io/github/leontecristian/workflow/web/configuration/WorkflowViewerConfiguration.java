package io.github.leontecristian.workflow.web.configuration;

import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.web.integration.instance.WorkflowViewerAccess;
import io.github.leontecristian.workflow.web.controller.instance.WorkflowInstanceViewerController;
import io.github.leontecristian.workflow.web.service.instance.ViewerWire;
import io.github.leontecristian.workflow.web.service.instance.WorkflowInstanceViewerService;
import io.github.leontecristian.workflow.web.service.instance.WorkflowInstanceGraphService;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.util.Map;

/** Import explicitly and supply WorkflowViewerOptions and WorkflowViewerAccess.
 * Deliberately has no component/configuration stereotype: scanning the JAR activates nothing.
 */
public class WorkflowViewerConfiguration {
    @Bean public ViewerWire workflowViewerWire() { return new ViewerWire(); }
    @Bean public WorkflowInstanceViewerService workflowInstanceViewerService(WorkflowRuntimeQueryService reads,
            WorkflowViewerAccess access, ViewerWire wire) { return new WorkflowInstanceViewerService(reads, access, wire); }
    @Bean public WorkflowInstanceGraphService workflowInstanceGraphService(WorkflowRuntimeQueryService reads,
            WorkflowDefinitionService definitions, WorkflowViewerAccess access, ViewerWire wire) {
        return new WorkflowInstanceGraphService(access, definitions, reads, wire);
    }
    @Bean public WorkflowInstanceViewerController workflowInstanceViewerController(WorkflowInstanceViewerService viewer,
            WorkflowInstanceGraphService graphs, ViewerWire wire, WorkflowViewerOptions options) {
        return new WorkflowInstanceViewerController(viewer, graphs, wire, options);
    }
    @Bean public RequestMappingHandlerMapping workflowViewerHandlerMapping(WorkflowViewerOptions options) {
        var mapping = new RequestMappingHandlerMapping() {
            @Override protected boolean isHandler(Class<?> type) { return type == WorkflowInstanceViewerController.class; }
            @Override protected org.springframework.web.method.HandlerMethod handleNoMatch(
                    java.util.Set<org.springframework.web.servlet.mvc.method.RequestMappingInfo> infos,
                    String lookupPath, jakarta.servlet.http.HttpServletRequest request) throws jakarta.servlet.ServletException {
                try { return super.handleNoMatch(infos, lookupPath, request); }
                catch (org.springframework.web.HttpRequestMethodNotSupportedException differentMethod) {
                    // A read pattern such as /instances/{id} must not preempt the host's POST /instances/start.
                    return null;
                }
            }
        };
        mapping.setOrder(-1);
        mapping.setPathPrefixes(Map.of(options.basePath(), type -> type == WorkflowInstanceViewerController.class));
        return mapping;
    }
}
