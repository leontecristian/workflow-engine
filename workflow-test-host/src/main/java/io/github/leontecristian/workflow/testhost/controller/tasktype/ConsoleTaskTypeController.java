package io.github.leontecristian.workflow.testhost.controller.tasktype;

import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.after;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.bool;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.decimal;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.limit;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.query;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.text;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.token;

import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeFilter;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeQuery;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeView;
import io.github.leontecristian.workflow.core.dto.tasktype.UpdateTaskType;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleErrors;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.definition.ConsoleManagement;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/console/api/task-types")
@PreAuthorize("hasRole('CONSOLE_ADMIN')")
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
public class ConsoleTaskTypeController {
    private final TaskTypeCatalogService catalog;
    private final ConsoleManagement management;
    private final ConsoleWire wire;

    public ConsoleTaskTypeController(
            TaskTypeCatalogService catalog, ConsoleManagement management, ConsoleWire wire) {
        this.catalog = catalog;
        this.management = management;
        this.wire = wire;
    }

    @GetMapping
    public ResponseEntity<String> list(HttpServletRequest r) {
        query(r, "filter", "afterId", "limit");
        TaskTypeFilter filter;
        try {
            filter =
                    TaskTypeFilter.valueOf(
                            r.getParameter("filter") == null ? "ALL" : r.getParameter("filter"));
        } catch (IllegalArgumentException e) {
            throw ConsoleException.invalid();
        }
        var p =
                catalog.list(
                        new TaskTypeQuery(
                                filter,
                                after(r.getParameter("afterId")),
                                limit(r.getParameter("limit"))));
        return wire.response(
                wire.page(p.items(), p.nextAfterId(), "nextAfterId", TaskTypeView::id));
    }

    @PostMapping
    public ResponseEntity<String> create(HttpServletRequest r) {
        var b = wire.body(r, "code", "name", "description", "selectable");
        return wire.response(
                management.createTaskType(
                        new CreateTaskType(
                                text(b, "code", false),
                                text(b, "name", false),
                                text(b, "description", true),
                                bool(b, "selectable")),
                        ConsoleErrors.correlation(r)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable String id, HttpServletRequest r) {
        var b = wire.body(r, "name", "description", "selectable");
        return wire.response(
                management.updateTaskType(
                        new UpdateTaskType(
                                decimal(id, false),
                                token(r),
                                text(b, "name", false),
                                text(b, "description", true),
                                bool(b, "selectable")),
                        ConsoleErrors.correlation(r)));
    }
}
