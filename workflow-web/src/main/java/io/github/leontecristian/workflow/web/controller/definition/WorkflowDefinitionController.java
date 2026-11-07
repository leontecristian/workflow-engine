package io.github.leontecristian.workflow.web.controller.definition;

import static io.github.leontecristian.workflow.web.service.administration.AdministrationWire.after;
import static io.github.leontecristian.workflow.web.service.administration.AdministrationWire.decimal;
import static io.github.leontecristian.workflow.web.service.administration.AdministrationWire.limit;
import static io.github.leontecristian.workflow.web.service.administration.AdministrationWire.query;
import static io.github.leontecristian.workflow.web.service.administration.AdministrationWire.text;
import static io.github.leontecristian.workflow.web.service.administration.AdministrationWire.token;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionQuery;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionOrder;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionQuery;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionSummary;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionView;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ResponseBody

public class WorkflowDefinitionController {
    private final WorkflowDefinitionService definitions;
    private final WorkflowAdministrationHost management;
    private final AdministrationWire wire;
    private final AdministrationErrors errors;

    public WorkflowDefinitionController(
            WorkflowDefinitionService definitions, WorkflowAdministrationHost management,
            AdministrationWire wire, AdministrationErrors errors) {
        this.definitions = definitions;
        this.management = management;
        this.wire = wire;
        this.errors = errors;
    }

    @GetMapping("/definitions")
    public ResponseEntity<String> definitions(HttpServletRequest r) {
        management.requireAdministrationAccess();
        query(r, "afterId", "limit", "order");
        DefinitionOrder order = DefinitionOrder.ASC;
        if (r.getParameter("order") != null) {
            try { order = DefinitionOrder.valueOf(r.getParameter("order")); }
            catch (IllegalArgumentException invalid) { throw WorkflowAdministrationException.invalid(); }
        }
        var page =
                definitions.listDefinitions(
                        new DefinitionQuery(
                                after(r.getParameter("afterId")), limit(r.getParameter("limit")), order));
        return wire.response(
                wire.page(page.items(), page.nextAfterId(), "nextAfterId", DefinitionView::id));
    }

    @GetMapping("/definitions/{id}")
    public ResponseEntity<String> definition(@PathVariable String id) {
        management.requireAdministrationAccess();
        return wire.response(
                definitions
                        .findDefinition(decimal(id, false))
                        .orElseThrow(WorkflowAdministrationException::missing));
    }

    @PutMapping("/definitions/{id}")
    public ResponseEntity<String> metadata(@PathVariable String id, HttpServletRequest r) {
        management.requireAdministrationAccess();
        var b = wire.body(r, "name", "description");
        return wire.response(
                management.metadata(
                        decimal(id, false),
                        token(r),
                        text(b, "name", false),
                        text(b, "description", true),
                        errors.correlation(r)));
    }

    @GetMapping("/definitions/{id}/versions")
    public ResponseEntity<String> versions(@PathVariable String id, HttpServletRequest r) {
        management.requireAdministrationAccess();
        query(r, "status", "afterId", "limit");
        DefinitionStatus status = null;
        if (r.getParameter("status") != null)
            try {
                status = DefinitionStatus.valueOf(r.getParameter("status"));
            } catch (IllegalArgumentException e) {
                throw WorkflowAdministrationException.invalid();
            }
        var page =
                definitions.listVersions(
                        new DefinitionVersionQuery(
                                decimal(id, false),
                                status,
                                after(r.getParameter("afterId")),
                                limit(r.getParameter("limit"))));
        return wire.response(
                wire.page(
                        page.items(),
                        page.nextAfterId(),
                        "nextAfterId",
                        DefinitionVersionSummary::id));
    }

    @GetMapping("/versions/lookup")
    public ResponseEntity<String> lookup(HttpServletRequest r) {
        management.requireAdministrationAccess();
        query(r, "key", "version");
        long version = decimal(r.getParameter("version"), false);
        if (version > Integer.MAX_VALUE) throw WorkflowAdministrationException.invalid();
        return wire.response(
                definitions
                        .findVersionByKey(r.getParameter("key"), (int) version)
                        .orElseThrow(WorkflowAdministrationException::missing));
    }

    @GetMapping("/versions/{id}")
    public ResponseEntity<String> version(@PathVariable String id) {
        management.requireAdministrationAccess();
        return wire.response(
                definitions.findVersion(decimal(id, false)).orElseThrow(WorkflowAdministrationException::missing));
    }

    @PostMapping("/versions/import")
    public ResponseEntity<String> importDraft(HttpServletRequest r) {
        management.requireAdministrationAccess();
        return wire.response(
                management.importDraft(wire.raw(r, 4 * 1024 * 1024), errors.correlation(r)));
    }

    @PutMapping("/versions/{id}/document")
    public ResponseEntity<String> replace(@PathVariable String id, HttpServletRequest r) {
        management.requireAdministrationAccess();
        return wire.response(
                management.replace(
                        decimal(id, false),
                        token(r),
                        wire.raw(r, 4 * 1024 * 1024),
                        errors.correlation(r)));
    }

    @PostMapping("/versions/{id}/validate")
    public ResponseEntity<String> validate(@PathVariable String id, HttpServletRequest r) {
        management.requireAdministrationAccess();
        wire.empty(r);
        return wire.response(management.validate(decimal(id, false), token(r), errors.correlation(r)));
    }

    @PostMapping("/versions/{id}/publish")
    public ResponseEntity<String> publish(@PathVariable String id, HttpServletRequest r) {
        management.requireAdministrationAccess();
        wire.empty(r);
        return wire.response(management.publish(decimal(id, false), token(r), errors.correlation(r)));
    }

    @PostMapping("/versions/{id}/retire")
    public ResponseEntity<String> retire(@PathVariable String id, HttpServletRequest r) {
        management.requireAdministrationAccess();
        wire.empty(r);
        return wire.response(management.retire(decimal(id, false), token(r), errors.correlation(r)));
    }

    @GetMapping("/versions/{id}/export")
    public ResponseEntity<String> export(@PathVariable String id) {
        management.requireAdministrationAccess();
        long key = decimal(id, false);
        return ResponseEntity.ok()
                .header("Content-Type", "application/json; charset=UTF-8")
                .header(
                        "Content-Disposition",
                        "attachment; filename=\"workflow-version-" + key + ".json\"")
                .body(definitions.exportVersion(key));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> error(Exception failure, HttpServletRequest request) {
        return errors.error(failure, request);
    }
}
