package io.github.leontecristian.workflow.testhost.controller.definition;

import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.after;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.decimal;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.limit;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.query;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.text;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.token;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionQuery;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionStatus;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionQuery;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionSummary;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionView;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
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
@RequestMapping("/console/api")
@PreAuthorize("hasRole('CONSOLE_ADMIN')")
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
public class ConsoleDefinitionController {
    private final WorkflowDefinitionService definitions;
    private final ConsoleManagement management;
    private final ConsoleWire wire;

    public ConsoleDefinitionController(
            WorkflowDefinitionService definitions, ConsoleManagement management, ConsoleWire wire) {
        this.definitions = definitions;
        this.management = management;
        this.wire = wire;
    }

    @GetMapping("/definitions")
    public ResponseEntity<String> definitions(HttpServletRequest r) {
        query(r, "afterId", "limit");
        var page =
                definitions.listDefinitions(
                        new DefinitionQuery(
                                after(r.getParameter("afterId")), limit(r.getParameter("limit"))));
        return wire.response(
                wire.page(page.items(), page.nextAfterId(), "nextAfterId", DefinitionView::id));
    }

    @GetMapping("/definitions/{id}")
    public ResponseEntity<String> definition(@PathVariable String id) {
        return wire.response(
                definitions
                        .findDefinition(decimal(id, false))
                        .orElseThrow(ConsoleException::missing));
    }

    @PutMapping("/definitions/{id}")
    public ResponseEntity<String> metadata(@PathVariable String id, HttpServletRequest r) {
        var b = wire.body(r, "name", "description");
        return wire.response(
                management.metadata(
                        decimal(id, false),
                        token(r),
                        text(b, "name", false),
                        text(b, "description", true),
                        ConsoleErrors.correlation(r)));
    }

    @GetMapping("/definitions/{id}/versions")
    public ResponseEntity<String> versions(@PathVariable String id, HttpServletRequest r) {
        query(r, "status", "afterId", "limit");
        DefinitionStatus status = null;
        if (r.getParameter("status") != null)
            try {
                status = DefinitionStatus.valueOf(r.getParameter("status"));
            } catch (IllegalArgumentException e) {
                throw ConsoleException.invalid();
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
        query(r, "key", "version");
        long version = decimal(r.getParameter("version"), false);
        if (version > Integer.MAX_VALUE) throw ConsoleException.invalid();
        return wire.response(
                definitions
                        .findVersionByKey(r.getParameter("key"), (int) version)
                        .orElseThrow(ConsoleException::missing));
    }

    @GetMapping("/versions/{id}")
    public ResponseEntity<String> version(@PathVariable String id) {
        return wire.response(
                definitions.findVersion(decimal(id, false)).orElseThrow(ConsoleException::missing));
    }

    @PostMapping("/versions/import")
    public ResponseEntity<String> importDraft(HttpServletRequest r) {
        return wire.response(
                management.importDraft(wire.raw(r, 4 * 1024 * 1024), ConsoleErrors.correlation(r)));
    }

    @PutMapping("/versions/{id}/document")
    public ResponseEntity<String> replace(@PathVariable String id, HttpServletRequest r) {
        return wire.response(
                management.replace(
                        decimal(id, false),
                        token(r),
                        wire.raw(r, 4 * 1024 * 1024),
                        ConsoleErrors.correlation(r)));
    }

    @PostMapping("/versions/{id}/{action:validate|publish|retire}")
    public ResponseEntity<String> lifecycle(
            @PathVariable String id, @PathVariable String action, HttpServletRequest r) {
        wire.empty(r);
        return wire.response(
                management.lifecycle(
                        decimal(id, false), token(r), action, ConsoleErrors.correlation(r)));
    }

    @GetMapping("/versions/{id}/export")
    public ResponseEntity<String> export(@PathVariable String id) {
        long key = decimal(id, false);
        return ResponseEntity.ok()
                .header("Content-Type", "application/json; charset=UTF-8")
                .header(
                        "Content-Disposition",
                        "attachment; filename=\"workflow-version-" + key + ".json\"")
                .body(definitions.exportVersion(key));
    }
}
