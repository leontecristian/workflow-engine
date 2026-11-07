package io.github.leontecristian.workflow.web.controller.instance;

import static io.github.leontecristian.workflow.web.service.instance.ViewerWire.after;
import static io.github.leontecristian.workflow.web.service.instance.ViewerWire.decimal;
import static io.github.leontecristian.workflow.web.service.instance.ViewerWire.limit;
import static io.github.leontecristian.workflow.web.service.instance.ViewerWire.query;
import io.github.leontecristian.workflow.web.service.instance.ViewerWire;
import io.github.leontecristian.workflow.web.service.instance.WorkflowInstanceViewerService;
import io.github.leontecristian.workflow.web.service.instance.WorkflowInstanceGraphService;
import io.github.leontecristian.workflow.web.configuration.WorkflowViewerOptions;
import io.github.leontecristian.workflow.web.exception.instance.WorkflowViewerException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import java.util.List;

/** Registered only by the explicitly imported viewer mapping, never by component scanning. */
@ResponseBody
public class WorkflowInstanceViewerController {
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowInstanceViewerController.class);
    private final WorkflowInstanceViewerService viewer;
    private final WorkflowInstanceGraphService graphs;
    private final ViewerWire wire;
    private final WorkflowViewerOptions options;
    public WorkflowInstanceViewerController(WorkflowInstanceViewerService viewer, WorkflowInstanceGraphService graphs,
            ViewerWire wire, WorkflowViewerOptions options) {
        this.viewer = viewer; this.graphs = graphs; this.wire = wire; this.options = options;
    }
    @GetMapping("/instances/{id}")
    public ResponseEntity<String> instance(@PathVariable String id) { return wire.response(viewer.instance(decimal(id, false))); }
    @GetMapping("/instances/{id}/history")
    public ResponseEntity<String> history(@PathVariable String id, HttpServletRequest request) {
        query(request, "afterSequence", "limit");
        return wire.response(viewer.history(decimal(id, false), after(request.getParameter("afterSequence")), limit(request.getParameter("limit"))));
    }
    @GetMapping("/instances/{id}/history/{sequence}")
    public ResponseEntity<String> event(@PathVariable String id, @PathVariable String sequence) {
        return ResponseEntity.ok().header("Content-Type", "application/json").body(viewer.event(decimal(id, false), decimal(sequence, false)));
    }
    @GetMapping("/instances/{id}/graph")
    public ResponseEntity<String> graph(@PathVariable String id, HttpServletRequest request) {
        query(request); return wire.response(graphs.find(decimal(id, false)));
    }
    public record Failure(String code, String message, List<?> diagnostics, String correlationId) {}
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> error(Exception failure, HttpServletRequest request) {
        int status = 500; String code = "HOST_FAILURE";
        if (failure instanceof WorkflowViewerException e) { status = e.status(); code = e.code(); }
        else if (failure instanceof WorkflowExecutionException e) {
            switch (e.category()) {
                case INVALID_INPUT -> { status = 400; code = "INVALID_INPUT"; }
                case NOT_FOUND -> { status = 404; code = "NOT_FOUND"; }
                case ASSIGNMENT_LOOKUP_FAILED -> { status = 503; code = "ASSIGNMENT_LOOKUP_FAILED"; }
                case LIMIT_EXCEEDED -> { status = 422; code = "LIMIT_EXCEEDED"; }
                default -> { status = 500; code = "HOST_CONFIGURATION"; }
            }
        }
        Object correlation = options.correlationAttribute() == null ? null : request.getAttribute(options.correlationAttribute());
        if (status >= 500)
            LOG.error("Workflow viewer failure {} ({})", correlation instanceof String value ? value : null, code, failure);
        return ResponseEntity.status(status).header("Content-Type", "application/json")
                .body(wire.json(new Failure(code, code, List.of(), correlation instanceof String value ? value : null)));
    }
}
