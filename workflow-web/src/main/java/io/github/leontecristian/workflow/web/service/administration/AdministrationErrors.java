package io.github.leontecristian.workflow.web.service.administration;

import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import io.github.leontecristian.workflow.core.exception.tasktype.TaskTypeCatalogException;
import io.github.leontecristian.workflow.web.exception.administration.WorkflowAdministrationException;
import io.github.leontecristian.workflow.web.configuration.WorkflowAdministrationOptions;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import java.util.List;

/** Invoked only by administration controller-local handlers, never global advice. */
public class AdministrationErrors {
    private final AdministrationWire wire;
    private final WorkflowAdministrationOptions options;
    public AdministrationErrors(AdministrationWire wire, WorkflowAdministrationOptions options) {
        this.wire = wire; this.options = options;
    }
    public String correlation(HttpServletRequest request) {
        Object value = request.getAttribute(options.correlationAttribute());
        return value instanceof String text ? text : null;
    }
    public record Failure(String code, String message, Object diagnostics, String correlationId) {}
    public ResponseEntity<String> error(Exception failure, HttpServletRequest request) {
        int status = 500;
        String code = "HOST_FAILURE";
        Object diagnostics = List.of();
        if (failure instanceof WorkflowAdministrationException e) {
            status = e.status(); code = e.code();
        } else if (failure instanceof WorkflowDefinitionException e) {
            code = e.category().name();
            diagnostics = e.diagnostics();
            status =
                    switch (e.category()) {
                        case INVALID_INPUT -> 400;
                        case NOT_FOUND -> 404;
                        case CONFLICT, INVALID_STATE -> 409;
                        default -> 500;
                    };
        } else if (failure instanceof TaskTypeCatalogException e) {
            code = e.getCategory().name();
            status =
                    switch (e.getCategory()) {
                        case INVALID_INPUT -> 400;
                        case NOT_FOUND -> 404;
                        case DUPLICATE_CODE, STALE_VERSION -> 409;
                        default -> 500;
                    };

        } else if (failure instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
                || failure instanceof org.springframework.http.InvalidMediaTypeException) {
            status = 400; code = "INVALID_INPUT";
        }
        if (status == 500)
            org.slf4j.LoggerFactory.getLogger(AdministrationErrors.class)
                    .error("Workflow administration failure {} ({})", correlation(request), code, failure);
        return ResponseEntity.status(status).header("Content-Type", "application/json")
                .body(wire.json(new Failure(code, code, diagnostics, correlation(request))));
    }
}
