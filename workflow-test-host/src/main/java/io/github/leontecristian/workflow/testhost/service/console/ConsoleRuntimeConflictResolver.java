package io.github.leontecristian.workflow.testhost.service.console;

import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.exception.console.SandboxRequestConflict;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeOptions;
import io.github.leontecristian.workflow.web.controller.runtime.WorkflowRuntimeCommandController;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeErrors;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeWire;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/** Renders sandbox-owned failures before the shared command controller's local handler. */
public final class ConsoleRuntimeConflictResolver implements HandlerExceptionResolver, Ordered {
    private final RuntimeErrors errors;

    public ConsoleRuntimeConflictResolver(WorkflowRuntimeOptions options) {
        this.errors = new RuntimeErrors(new RuntimeWire(), options);
    }

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception failure) {
        if (!(handler instanceof HandlerMethod method)
                || method.getBeanType() != WorkflowRuntimeCommandController.class) {
            return null;
        }
        ConsoleException consoleFailure = failure instanceof ConsoleException direct ? direct : null;
        if (ConsoleErrors.hasSandboxConflict(failure)) {
            consoleFailure = new SandboxRequestConflict();
        }
        if (consoleFailure == null) {
            return null;
        }
        var translated = new WorkflowRuntimeException(consoleFailure.status(), consoleFailure.code());
        translated.initCause(failure);
        var envelope = errors.error(translated, request);
        response.setStatus(envelope.getStatusCode().value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            response.getWriter().write(envelope.getBody());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ModelAndView();
    }
}
