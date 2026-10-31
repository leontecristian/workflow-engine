package io.github.leontecristian.workflow.testhost.controller.event;

import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.after;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.id;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.limit;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.query;

import io.github.leontecristian.workflow.testhost.dto.event.InboxView;
import io.github.leontecristian.workflow.testhost.dto.event.OutboxView;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.repository.event.ConsoleEventStore;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.event.ConsoleEventDelivery;

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
@RequestMapping("/console/api")
@PreAuthorize("hasRole('CONSOLE_ADMIN')")
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
public class ConsoleEventController {
    private final ConsoleEventStore events;
    private final ConsoleEventDelivery delivery;
    private final ConsoleWire wire;

    public ConsoleEventController(
            ConsoleEventStore events, ConsoleEventDelivery delivery, ConsoleWire wire) {
        this.events = events;
        this.delivery = delivery;
        this.wire = wire;
    }

    @GetMapping("/events")
    public ResponseEntity<String> events(HttpServletRequest r) {
        query(r, "status", "afterId", "limit");
        int n = limit(r.getParameter("limit"));
        var rows =
                events.page(
                        r.getParameter("status") == null ? "ALL" : r.getParameter("status"),
                        after(r.getParameter("afterId")),
                        n + 1);
        return wire.response(
                wire.page(
                        rows.stream().limit(n).toList(),
                        rows.size() > n ? rows.get(n - 1).id() : null,
                        "nextAfterId",
                        OutboxView::id));
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<String> event(@PathVariable String id) {
        return wire.response(events.find(id, false));
    }

    @GetMapping("/inbox")
    public ResponseEntity<String> inbox(HttpServletRequest r) {
        query(r, "afterId", "limit");
        int n = limit(r.getParameter("limit"));
        var rows = events.inbox(after(r.getParameter("afterId")), n + 1);
        return wire.response(
                wire.page(
                        rows.stream().limit(n).toList(),
                        rows.size() > n ? rows.get(n - 1).id() : null,
                        "nextAfterId",
                        InboxView::id));
    }

    @GetMapping("/inbox/{id}")
    public ResponseEntity<String> inboxEvent(@PathVariable String id) {
        var value = events.findInbox(id);
        if (value == null) throw ConsoleException.missing();
        return wire.response(value);
    }

    @PostMapping("/events/dispatch")
    public ResponseEntity<String> dispatch(HttpServletRequest r) {
        var b = wire.body(r, "limit");
        int count = 50;
        if (b.has("limit")) {
            if (!b.get("limit").isIntegralNumber() || !b.get("limit").canConvertToInt())
                throw ConsoleException.invalid();
            count = b.get("limit").intValue();
            if (count < 1 || count > 100) throw ConsoleException.invalid();
        }
        return wire.response(delivery.dispatch(count));
    }

    @PostMapping("/events/{id}/redeliver")
    public ResponseEntity<String> redeliver(@PathVariable String id, HttpServletRequest r) {
        wire.empty(r);
        return wire.response(delivery.redeliver(id));
    }
}
