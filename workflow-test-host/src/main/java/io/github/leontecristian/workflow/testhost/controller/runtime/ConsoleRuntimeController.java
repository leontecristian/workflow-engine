package io.github.leontecristian.workflow.testhost.controller.runtime;

import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.after;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.decimal;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.id;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.limit;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.query;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.uuid;

import io.github.leontecristian.workflow.core.dto.runtime.BooleanLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.NullLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.StartInputs;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowLiteral;
import io.github.leontecristian.workflow.testhost.dto.runtime.SandboxStart;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleErrors;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeCoordinator;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
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
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
public class ConsoleRuntimeController {
    private final ConsoleRuntimeAccess access;
    private final ConsoleRuntimeCoordinator commands;
    private final ConsoleRuntimeTransaction transactions;
    private final ConsoleContext context;
    private final ConsoleWire wire;
    private final io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxServiceEffectStore effects;

    public ConsoleRuntimeController(ConsoleRuntimeAccess access, ConsoleRuntimeCoordinator commands,
            ConsoleRuntimeTransaction transactions, ConsoleContext context, ConsoleWire wire,
            io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxServiceEffectStore effects) {
        this.access = access; this.commands = commands; this.transactions = transactions;
        this.context = context; this.wire = wire; this.effects = effects;
    }
    private SandboxStart startBody(HttpServletRequest r) {
        var b =
                wire.body(
                        r, 2*1024*1024,
                        "commandId",
                        "definitionVersionId",
                        "requestId",
                        "expectedRequestVersion", "inputs");
        return new SandboxStart(
                uuid(b),
                id(b, "definitionVersionId", false),
                id(b, "requestId", false),
                id(b, "expectedRequestVersion", true), inputs(b));
    }
    private StartInputs inputs(com.fasterxml.jackson.databind.JsonNode body) {
        if (!body.has("inputs")) return StartInputs.empty();
        var n=body.get("inputs");
        if (!n.isObject()) throw ConsoleException.invalid();
        if (n.size()>100) throw new ConsoleException(422,"LIMIT_EXCEEDED");
        var values=new java.util.HashMap<String,WorkflowLiteral>();
        n.fields().forEachRemaining(e -> {
            var v=e.getValue(); WorkflowLiteral token;
            if(v.isNull()) token=NullLiteral.INSTANCE;
            else if(v.isBoolean()) token=new BooleanLiteral(v.booleanValue());
            else if(v.isTextual()) token=new TextLiteral(v.textValue());
            else if(v.isContainerNode()) token=io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue.parse(v.toString());
            else throw ConsoleException.invalid();
            values.put(e.getKey(),token);
        });
        return new StartInputs(values);
    }
    @GetMapping("/instances/{id}/service-effects")
    @PreAuthorize("hasRole('CONSOLE_ADMIN')")
    public ResponseEntity<String> effects(@PathVariable String id,HttpServletRequest r) {
        query(r,"afterId","limit"); long key=decimal(id,false); access.instance(key);
        int count=limit(r.getParameter("limit")); var rows=effects.page(key,after(r.getParameter("afterId")),count+1);
        Long next=rows.size()>count ? rows.get(count-1).id() : null;
        return wire.response(wire.page(rows.subList(0,Math.min(count,rows.size())),next,"nextAfterId",io.github.leontecristian.workflow.testhost.dto.sandbox.SandboxServiceEffect::id));
    }
    @PostMapping("/instances/start")
    @PreAuthorize("hasRole('CONSOLE_ADMIN')")
    public ResponseEntity<String> start(HttpServletRequest r) {
        return wire.response(
                commands.start(startBody(r), context.runtime(ConsoleErrors.correlation(r))));
    }
    @PostMapping("/commands/start/replay")
    @PreAuthorize("hasRole('CONSOLE_ADMIN')")
    public ResponseEntity<String> replayStart(HttpServletRequest r) {
        return wire.response(
                replay(
                        transactions
                                .replayStart(
                                        startBody(r), context.runtime(ConsoleErrors.correlation(r)))
                                .orElse(null)));
    }
    private Map<String, Object> replay(WorkflowCommandResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("found", result != null);
        map.put("result", result);
        return map;
    }
}
