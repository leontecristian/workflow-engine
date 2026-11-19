package io.github.leontecristian.workflow.testhost.controller.runtime;

import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.decimal;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.limit;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.query;

import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleBusinessHistory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Business history under the sandbox instance and event grants. */
@RestController
@RequestMapping("/console/api/instances/{id}/business-history")
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
public class ConsoleBusinessHistoryController {
    private final ConsoleContext identity;
    private final ConsoleBusinessHistory history;
    private final ConsoleWire wire;

    public ConsoleBusinessHistoryController(ConsoleContext identity, ConsoleBusinessHistory history, ConsoleWire wire) {
        this.identity = identity;
        this.history = history;
        this.wire = wire;
    }

    @GetMapping
    public ResponseEntity<String> history(@PathVariable String id, HttpServletRequest request) {
        query(request, "cursor", "limit");
        return wire.response(history.page(identity.actor(), decimal(id, false), request.getParameter("cursor"),
                limit(request.getParameter("limit"))));
    }

    @GetMapping("/{eventRef}")
    public ResponseEntity<String> event(@PathVariable String id, @PathVariable String eventRef, HttpServletRequest request) {
        query(request);
        return wire.response(history.event(identity.actor(), decimal(id, false), eventRef));
    }
}
