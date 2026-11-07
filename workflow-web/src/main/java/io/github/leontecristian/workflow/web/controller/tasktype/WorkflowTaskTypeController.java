package io.github.leontecristian.workflow.web.controller.tasktype;

import static io.github.leontecristian.workflow.web.service.administration.AdministrationWire.after;
import static io.github.leontecristian.workflow.web.service.administration.AdministrationWire.bool;
import static io.github.leontecristian.workflow.web.service.administration.AdministrationWire.decimal;
import static io.github.leontecristian.workflow.web.service.administration.AdministrationWire.limit;
import static io.github.leontecristian.workflow.web.service.administration.AdministrationWire.query;
import static io.github.leontecristian.workflow.web.service.administration.AdministrationWire.text;
import static io.github.leontecristian.workflow.web.service.administration.AdministrationWire.token;

import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeFilter;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeQuery;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeView;
import io.github.leontecristian.workflow.core.dto.tasktype.UpdateTaskType;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.web.exception.administration.WorkflowAdministrationException;
import io.github.leontecristian.workflow.web.service.administration.AdministrationErrors;
import io.github.leontecristian.workflow.web.service.administration.AdministrationWire;
import io.github.leontecristian.workflow.web.integration.administration.WorkflowAdministrationHost;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ResponseBody
@RequestMapping("/task-types")

public class WorkflowTaskTypeController {
    private final TaskTypeCatalogService catalog;
    private final WorkflowAdministrationHost management;
    private final AdministrationWire wire;
    private final AdministrationErrors errors;

    public WorkflowTaskTypeController(
            TaskTypeCatalogService catalog, WorkflowAdministrationHost management,
            AdministrationWire wire, AdministrationErrors errors) {
        this.catalog = catalog;
        this.management = management;
        this.wire = wire;
        this.errors = errors;
    }

    @GetMapping
    public ResponseEntity<String> list(HttpServletRequest r) {
        management.requireAdministrationAccess();
        query(r, "filter", "afterId", "limit");
        TaskTypeFilter filter;
        try {
            filter =
                    TaskTypeFilter.valueOf(
                            r.getParameter("filter") == null ? "ALL" : r.getParameter("filter"));
        } catch (IllegalArgumentException e) {
            throw WorkflowAdministrationException.invalid();
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
        management.requireAdministrationAccess();
        var b = wire.body(r, "code", "name", "description", "selectable");
        return wire.response(
                management.createTaskType(
                        new CreateTaskType(
                                text(b, "code", false),
                                text(b, "name", false),
                                text(b, "description", true),
                                bool(b, "selectable")),
                        errors.correlation(r)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable String id, HttpServletRequest r) {
        management.requireAdministrationAccess();
        var b = wire.body(r, "name", "description", "selectable");
        return wire.response(
                management.updateTaskType(
                        new UpdateTaskType(
                                decimal(id, false),
                                token(r),
                                text(b, "name", false),
                                text(b, "description", true),
                                bool(b, "selectable")),
                        errors.correlation(r)));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> error(Exception failure, HttpServletRequest request) {
        return errors.error(failure, request);
    }
}
