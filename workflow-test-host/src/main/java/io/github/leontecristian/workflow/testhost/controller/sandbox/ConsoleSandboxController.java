package io.github.leontecristian.workflow.testhost.controller.sandbox;

import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.after;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.decimal;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.id;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.limit;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.query;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.text;

import io.github.leontecristian.workflow.testhost.dto.sandbox.SandboxRequestView;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.sandbox.ConsoleSandboxService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/console/api/sandbox-requests")
@PreAuthorize("hasRole('CONSOLE_ADMIN')")
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
public class ConsoleSandboxController {
    private final SandboxRequestStore requests;
    private final ConsoleSandboxService service;
    private final ConsoleWire wire;

    public ConsoleSandboxController(
            SandboxRequestStore requests, ConsoleSandboxService service, ConsoleWire wire) {
        this.requests = requests;
        this.service = service;
        this.wire = wire;
    }

    @PostMapping
    public ResponseEntity<String> create(HttpServletRequest r) {
        return wire.response(service.create(text(wire.body(r, "name"), "name", false)));
    }

    @GetMapping
    public ResponseEntity<String> list(HttpServletRequest r) {
        query(r, "afterId", "limit");
        int n = limit(r.getParameter("limit"));
        var rows = requests.page(after(r.getParameter("afterId")), n + 1);
        return wire.response(
                wire.page(
                        rows.stream().limit(n).toList(),
                        rows.size() > n ? rows.get(n - 1).id() : null,
                        "nextAfterId",
                        SandboxRequestView::id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<String> exact(@PathVariable String id) {
        return wire.response(requests.find(decimal(id, false)));
    }
}
