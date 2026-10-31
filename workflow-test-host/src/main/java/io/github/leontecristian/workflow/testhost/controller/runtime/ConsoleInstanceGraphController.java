package io.github.leontecristian.workflow.testhost.controller.runtime;

import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.decimal;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.query;

import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleInstanceGraph;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
public class ConsoleInstanceGraphController {
    private final ConsoleInstanceGraph graphs;
    private final ConsoleWire wire;

    public ConsoleInstanceGraphController(ConsoleInstanceGraph graphs, ConsoleWire wire) {
        this.graphs = graphs;
        this.wire = wire;
    }

    @GetMapping("/console/api/instances/{id}/graph")
    public ResponseEntity<String> graph(@PathVariable String id, HttpServletRequest request) {
        query(request);
        return wire.response(graphs.find(decimal(id, false)));
    }
}
