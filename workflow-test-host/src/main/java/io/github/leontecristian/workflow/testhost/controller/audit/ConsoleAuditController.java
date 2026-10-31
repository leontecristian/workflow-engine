package io.github.leontecristian.workflow.testhost.controller.audit;

import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.after;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.decimal;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.id;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.limit;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.query;

import io.github.leontecristian.workflow.testhost.dto.audit.AuditView;
import io.github.leontecristian.workflow.testhost.repository.audit.ManagementAuditStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/console/api/audit")
@PreAuthorize("hasRole('CONSOLE_ADMIN')")
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
public class ConsoleAuditController {
    private final ManagementAuditStore audits;
    private final ConsoleWire wire;

    public ConsoleAuditController(ManagementAuditStore audits, ConsoleWire wire) {
        this.audits = audits;
        this.wire = wire;
    }

    @GetMapping
    public ResponseEntity<String> list(HttpServletRequest r) {
        query(r, "afterId", "limit");
        int n = limit(r.getParameter("limit"));
        var rows = audits.page(after(r.getParameter("afterId")), n + 1);
        Long next = rows.size() > n ? rows.get(n - 1).id() : null;
        return wire.response(
                wire.page(rows.stream().limit(n).toList(), next, "nextAfterId", AuditView::id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<String> exact(@PathVariable String id) {
        return wire.response(audits.find(decimal(id, false)));
    }
}
