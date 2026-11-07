package io.github.leontecristian.workflow.web.service.runtime;

import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeOptions;
import io.github.leontecristian.workflow.web.exception.instance.WorkflowViewerException;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
/** Invoked only from controller-local handlers. */
public class RuntimeErrors {
    private final RuntimeWire wire;
    private final WorkflowRuntimeOptions options;
    public RuntimeErrors(RuntimeWire wire, WorkflowRuntimeOptions options) { this.wire = wire; this.options = options; }
    public String correlation(HttpServletRequest request) {
        Object value = request.getAttribute(options.correlationAttribute());
        return value instanceof String text ? text : null;
    }
    public record Failure(String code, String message, Object diagnostics, String correlationId) {}
    public ResponseEntity<String> error(Exception failure, HttpServletRequest request) {
        int status = 500; String code = "HOST_FAILURE";
        if (failure instanceof WorkflowRuntimeException e) { status = e.status(); code = e.code(); }
        else if (failure instanceof WorkflowViewerException e) { status = e.status(); code = e.code(); }
        else if (failure instanceof WorkflowExecutionException e) {
            code = e.category().name();
            status = switch (e.category()) {
                case INVALID_INPUT -> 400;
                case NOT_FOUND -> 404;
                case NOT_ASSIGNED -> 403;
                case ASSIGNMENT_LOOKUP_FAILED -> 503;
                case STALE_VERSION, CONCURRENT_COMMAND, COMMAND_ID_CONFLICT, INVALID_STATE -> 409;
                case LIMIT_EXCEEDED, UNINITIALIZED_VARIABLE, ROUTE_NOT_FOUND,
                        EXPRESSION_EVALUATION_FAILED, EXPRESSION_LIMIT_EXCEEDED -> 422;
                default -> 500;
            };
        } else if (failure instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
                || failure instanceof org.springframework.http.InvalidMediaTypeException) {
            status = 400; code = "INVALID_INPUT";
        }
        if (status >= 500) org.slf4j.LoggerFactory.getLogger(RuntimeErrors.class)
                .error("Workflow runtime failure {} ({})", correlation(request), code, failure);
        return ResponseEntity.status(status).header("Content-Type", "application/json")
                .body(wire.json(new Failure(code, code, List.of(), correlation(request))));
    }
}
