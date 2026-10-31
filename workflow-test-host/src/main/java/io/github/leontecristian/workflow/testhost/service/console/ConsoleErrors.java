package io.github.leontecristian.workflow.testhost.service.console;

import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.tasktype.TaskTypeCatalogException;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.util.List;

@Component
@RestControllerAdvice
public class ConsoleErrors {
    private final ConsoleWire wire;

    public ConsoleErrors(ConsoleWire wire) {
        this.wire = wire;
    }

    public record Failure(String code, String message, Object diagnostics, String correlationId) {}

    public void write(
            HttpServletRequest request, HttpServletResponse response, int status, String code)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter()
                .write(wire.json(new Failure(code, code, List.of(), correlation(request))));
    }

    public static String correlation(HttpServletRequest request) {
        return (String) request.getAttribute("console.correlation");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> error(Exception failure, HttpServletRequest request) {
        int status = 500;
        String code = "HOST_FAILURE";
        Object diagnostics = List.of();
        if (failure instanceof ConsoleException e) {
            status = e.status();
            code = e.code();
        } else if (failure instanceof WorkflowExecutionException e) {
            code = e.category().name();
            status =
                    switch (e.category()) {
                        case INVALID_INPUT -> 400;
                        case NOT_FOUND -> 404;
                        case NOT_ASSIGNED -> 403;
                        case STALE_VERSION,
                                        CONCURRENT_COMMAND,
                                        COMMAND_ID_CONFLICT,
                                        INVALID_STATE ->
                                409;
                        case LIMIT_EXCEEDED -> 422;
                        default -> 500;
                    };
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
        } else if (failure instanceof org.springframework.security.access.AccessDeniedException) {
            status = 403;
            code = "FORBIDDEN";
        } else if (failure
                instanceof org.springframework.web.servlet.resource.NoResourceFoundException) {
            status = 404;
            code = "NOT_FOUND";
        } else if (failure
                        instanceof
                        org.springframework.web.method.annotation
                                .MethodArgumentTypeMismatchException
                || failure instanceof org.springframework.http.InvalidMediaTypeException) {
            status = 400;
            code = "INVALID_INPUT";
        }
        if (hasSandboxConflict(failure)) {
            status = 409;
            code = "SANDBOX_REQUEST_CONFLICT";
        }
        if (status == 500)
            LoggerFactory.getLogger(ConsoleErrors.class)
                    .error("Console failure {}", correlation(request), failure);
        return ResponseEntity.status(status)
                .header("Content-Type", "application/json")
                .body(wire.json(new Failure(code, code, diagnostics, correlation(request))));
    }

    private boolean hasSandboxConflict(Throwable failure) {
        var seen =
                java.util.Collections.newSetFromMap(
                        new java.util.IdentityHashMap<Throwable, Boolean>());
        for (Throwable item = failure; item != null && seen.add(item); item = item.getCause())
            if (item
                    instanceof
                    io.github.leontecristian.workflow.testhost.exception.console
                            .SandboxRequestConflict) return true;
        return false;
    }
}
