package io.github.leontecristian.workflow.web.service.runtime;

import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeOptions;
import io.github.leontecristian.workflow.web.exception.instance.WorkflowViewerException;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
/** Invoked only from controller-local handlers. */
public class RuntimeErrors {
    private static final int DIAGNOSTIC_BYTES = 8 * 1024;
    private static final int FAILURE_BYTES = 128 * 1024;
    private final RuntimeWire wire;
    private final WorkflowRuntimeOptions options;
    public RuntimeErrors(RuntimeWire wire, WorkflowRuntimeOptions options) { this.wire = wire; this.options = options; }
    public String correlation(HttpServletRequest request) {
        Object value = request.getAttribute(options.correlationAttribute());
        return value instanceof String text ? text : null;
    }
    public record Failure(String code, String message, List<Map<String, Object>> diagnostics, String correlationId) {}
    public ResponseEntity<String> error(Exception failure, HttpServletRequest request) {
        int status = 500; String code = "HOST_FAILURE";
        String message = null;
        List<Map<String, Object>> diagnostics = List.of();
        if (failure instanceof WorkflowRuntimeException e) { status = e.status(); code = e.code(); }
        else if (failure instanceof WorkflowViewerException e) { status = e.status(); code = e.code(); }
        else if (failure instanceof WorkflowExecutionException e) {
            code = e.category().name();
            message = e.safeMessage();
            status = switch (e.category()) {
                case INVALID_INPUT -> 400;
                case NOT_FOUND -> 404;
                case NOT_ASSIGNED, TASK_ASSIGNED_TO_OTHER, ADMIN_RELEASE_FORBIDDEN -> 403;
                case ASSIGNMENT_LOOKUP_FAILED, AUTHORIZATION_LOOKUP_FAILED, RESULT_VALIDATION_FAILED -> 503;
                case CONFLICT, STALE_VERSION, CONCURRENT_COMMAND, COMMAND_ID_CONFLICT, INVALID_STATE,
                        TASK_ALREADY_ASSIGNED, TASK_NOT_ASSIGNED, CLAIM_REQUIRED, TASK_NOT_CLAIMABLE -> 409;
                case LIMIT_EXCEEDED, UNINITIALIZED_VARIABLE, SUBMISSION_UNAVAILABLE, ROUTE_NOT_FOUND,
                        EXPRESSION_EVALUATION_FAILED, EXPRESSION_LIMIT_EXCEEDED, RESULT_SCHEMA_INVALID, RESULT_REJECTED -> 422;
                default -> 500;
            };
            if (e.category() == WorkflowExecutionException.Category.RESULT_SCHEMA_INVALID
                    || e.category() == WorkflowExecutionException.Category.RESULT_REJECTED) {
                diagnostics = List.of(resultDiagnostic(e));
            }
        } else if (failure instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
                || failure instanceof org.springframework.http.InvalidMediaTypeException) {
            status = 400; code = "INVALID_INPUT";
        }
        String correlation = correlation(request);
        if (status >= 500) org.slf4j.LoggerFactory.getLogger(RuntimeErrors.class)
                .error("Workflow runtime failure {} ({})", correlation, code, failure);
        String publicMessage = message == null || message.length() > 2000
                || message.codePointCount(0, message.length()) > 1000 || !RuntimeWire.unicode(message) ? code : message;
        String json = wire.json(new Failure(code, publicMessage, diagnostics, correlation));
        if (!fits(json, FAILURE_BYTES)) json = wire.json(new Failure(code, publicMessage, diagnostics, null));
        return ResponseEntity.status(status).header("Content-Type", "application/json")
                .body(json);
    }

    private Map<String, Object> resultDiagnostic(WorkflowExecutionException failure) {
        Map<String, Object> diagnostic = new LinkedHashMap<>();
        diagnostic.put("code", failure.category().name());
        diagnostic.put("message", failure.category() == WorkflowExecutionException.Category.RESULT_SCHEMA_INVALID
                ? "The result does not match the pinned schema." : "The result was rejected by the host.");
        var locations = failure.diagnostic();
        // Reserve space for the omission marker while testing the complete, escaped locations.
        diagnostic.put("locationOmitted", true);
        boolean pathFits = addLocation(diagnostic, "path", locations == null || locations.path() == null ? "" : locations.path());
        boolean schemaFits = addLocation(diagnostic, "schemaPath", locations == null ? null : locations.schemaPath());
        if (pathFits && schemaFits) diagnostic.remove("locationOmitted");
        return diagnostic;
    }

    private boolean addLocation(Map<String, Object> diagnostic, String key, String location) {
        if (location == null) return true;
        if (location.length() * 2L > DIAGNOSTIC_BYTES || !RuntimeWire.unicode(location)) return false;
        diagnostic.put(key, location);
        if (fits(wire.json(diagnostic), DIAGNOSTIC_BYTES)) return true;
        diagnostic.remove(key);
        return false;
    }

    private static boolean fits(String value, int bound) {
        return value.length() * 2L <= bound && RuntimeWire.utf8Length(value) <= bound;
    }
}
