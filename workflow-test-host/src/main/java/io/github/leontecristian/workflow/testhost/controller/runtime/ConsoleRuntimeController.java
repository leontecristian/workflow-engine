package io.github.leontecristian.workflow.testhost.controller.runtime;
import io.github.leontecristian.workflow.core.dto.runtime.BooleanLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.NullLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.ScalarLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.StartInputs;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;

import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.after;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.decimal;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.id;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.limit;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.query;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.text;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.token;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.uuid;

import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmission;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceQuery;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceSummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.dto.runtime.SandboxStart;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleErrors;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeCoordinator;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/console/api")
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
public class ConsoleRuntimeController {
    private final WorkflowRuntimeQueryService reads;
    private final WorkflowDefinitionService definitions;
    private final ConsoleRuntimeAccess access;
    private final ConsoleRuntimeCoordinator commands;
    private final ConsoleRuntimeTransaction transactions;
    private final ConsoleContext context;
    private final ConsoleWire wire;
    private final io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxServiceEffectStore effects;

    public ConsoleRuntimeController(
            WorkflowRuntimeQueryService reads,
            WorkflowDefinitionService definitions,
            ConsoleRuntimeAccess access,
            ConsoleRuntimeCoordinator commands,
            ConsoleRuntimeTransaction transactions,
            ConsoleContext context,
            ConsoleWire wire, io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxServiceEffectStore effects) {
        this.effects=effects;
        this.reads = reads;
        this.definitions = definitions;
        this.access = access;
        this.commands = commands;
        this.transactions = transactions;
        this.context = context;
        this.wire = wire;
    }

    private SandboxStart startBody(HttpServletRequest r) {
        var b =
                wire.body(
                        r, 256*1024,
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
        var values=new java.util.HashMap<String,ScalarLiteral>();
        n.fields().forEachRemaining(e -> {
            var v=e.getValue(); ScalarLiteral token;
            if(v.isNull()) token=NullLiteral.INSTANCE;
            else if(v.isBoolean()) token=new BooleanLiteral(v.booleanValue());
            else if(v.isTextual()) token=new TextLiteral(v.textValue());
            else throw ConsoleException.invalid();
            values.put(e.getKey(),token);
        });
        return new StartInputs(values);
    }
    @GetMapping("/instances/{id}/variables")
    public ResponseEntity<String> variables(@PathVariable String id,HttpServletRequest r) {
        query(r,"afterKey","limit","expectedLockVersion"); long key=decimal(id,false); access.instance(key);
        var page=reads.listVariables(key,r.getParameter("afterKey"),limit(r.getParameter("limit")),
                r.getParameter("expectedLockVersion")==null ? null : decimal(r.getParameter("expectedLockVersion"),true));
        var items=new java.util.ArrayList<Map<String,Object>>();
        for(var v:page.items()) {
            Map<String,Object> item=new LinkedHashMap<>(); item.put("key",v.key()); item.put("type",v.type()); item.put("initialized",v.initialized());
            Object value=null;
            if(v.value()!=null) {
                if(v.value().value() instanceof TextLiteral t) value=t.value();
                else if(v.value().value() instanceof BooleanLiteral t) value=t.value();
            }
            item.put("value",value); items.add(item);
        }
        Map<String,Object> result=new LinkedHashMap<>(); result.put("instanceId",page.instanceId()); result.put("lockVersion",page.lockVersion());
        result.put("items",items); result.put("nextAfterKey",page.nextAfterKey()); return wire.response(result);
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

    private CompleteWorkflowTask completeBody(HttpServletRequest r, long instance, long task) {
        var b = wire.body(r, "commandId", "outcomeKey", "comment");
        return new CompleteWorkflowTask(
                uuid(b),
                instance,
                task,
                token(r),
                text(b, "outcomeKey", false),
                text(b, "comment", true));
    }

    @PostMapping("/instances/{instanceId}/tasks/{taskId}/complete")
    public ResponseEntity<String> complete(
            @PathVariable String instanceId, @PathVariable String taskId, HttpServletRequest r) {
        // Completion reaches core ownership checks, including for administrators. No privileged
        // bypass.
        return wire.response(
                commands.complete(
                        completeBody(r, decimal(instanceId, false), decimal(taskId, false)),
                        context.runtime(ConsoleErrors.correlation(r))));
    }

    @PostMapping("/commands/complete/replay")
    public ResponseEntity<String> replayComplete(HttpServletRequest r) {
        var b = wire.body(r, "commandId", "instanceId", "taskId", "outcomeKey", "comment");
        var command =
                new CompleteWorkflowTask(
                        uuid(b),
                        id(b, "instanceId", false),
                        id(b, "taskId", false),
                        token(r),
                        text(b, "outcomeKey", false),
                        text(b, "comment", true));
        access.task(command.taskId());
        return wire.response(
                replay(
                        transactions
                                .replayComplete(
                                        command, context.runtime(ConsoleErrors.correlation(r)))
                                .orElse(null)));
    }

    private Map<String, Object> replay(WorkflowCommandResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("found", result != null);
        map.put("result", result);
        return map;
    }

    @GetMapping("/instances")
    @PreAuthorize("hasRole('CONSOLE_ADMIN')")
    public ResponseEntity<String> instances(HttpServletRequest r) {
        query(r, "status", "afterId", "limit");
        String requested = r.getParameter("status");
        InstanceStatus status = InstanceStatus.ACTIVE;
        if ("ALL".equals(requested)) status = null;
        else if (requested != null) {
            try {
                status = InstanceStatus.valueOf(requested);
            } catch (IllegalArgumentException invalid) {
                throw ConsoleException.invalid();
            }
        }
        var page =
                reads.listInstances(
                        new WorkflowInstanceQuery(
                                status,
                                after(r.getParameter("afterId")),
                                limit(r.getParameter("limit"))));
        return wire.response(
                wire.page(
                        page.items(),
                        page.nextAfterId(),
                        "nextAfterId",
                        WorkflowInstanceSummary::id));
    }

    @GetMapping("/instances/{id}")
    public ResponseEntity<String> instance(@PathVariable String id) {
        return wire.response(access.instance(decimal(id, false)));
    }

    @GetMapping("/instances/{id}/tasks")
    public ResponseEntity<String> tasks(@PathVariable String id, HttpServletRequest r) {
        query(r, "afterId", "limit");
        long key = decimal(id, false);
        access.instance(key);
        var page =
                reads.listTasks(
                        key, after(r.getParameter("afterId")), limit(r.getParameter("limit")));
        return wire.response(
                wire.page(page.items(), page.nextAfterId(), "nextAfterId", WorkflowTaskView::id));
    }

    @GetMapping("/instances/{id}/history")
    public ResponseEntity<String> history(@PathVariable String id, HttpServletRequest r) {
        query(r, "afterSequence", "limit");
        long key = decimal(id, false);
        access.instance(key);
        var page =
                reads.listHistory(
                        key,
                        after(r.getParameter("afterSequence")),
                        limit(r.getParameter("limit")));
        return wire.response(
                wire.page(
                        page.items(),
                        page.nextAfterSequence(),
                        "nextAfterSequence",
                        WorkflowHistorySummary::historySequence));
    }

    @GetMapping("/instances/{id}/history/{sequence}")
    public ResponseEntity<String> historyEvent(
            @PathVariable String id, @PathVariable String sequence) {
        long key = decimal(id, false);
        access.instance(key);
        return wire.response(
                reads.findHistoryEvent(key, decimal(sequence, false))
                        .orElseThrow(ConsoleException::missing));
    }

    @GetMapping("/tasks/mine")
    public ResponseEntity<String> mine(HttpServletRequest r) {
        query(r, "afterId", "limit");
        var page =
                reads.listAssignedTasks(
                        context.actor(),
                        after(r.getParameter("afterId")),
                        limit(r.getParameter("limit")));
        return wire.response(
                wire.page(page.items(), page.nextAfterId(), "nextAfterId", WorkflowTaskView::id));
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<String> task(@PathVariable String id) {
        return wire.response(access.task(decimal(id, false)));
    }

    @GetMapping("/tasks/{id}/submission")
    public ResponseEntity<String> submission(@PathVariable String id) {
        long key = decimal(id, false);
        access.task(key);
        return wire.response(reads.findTaskSubmission(key).orElse(null));
    }

    @GetMapping("/tasks/{id}/contract")
    public ResponseEntity<String> contract(@PathVariable String id) {
        var task = access.task(decimal(id, false));
        var instance = access.instance(task.instanceId());
        var activity = access.activity(task.activityId());
        var version =
                definitions
                        .findVersion(instance.definitionVersionId())
                        .orElseThrow(() -> new IllegalStateException("Pinned definition missing"));
        var nodes = wire.tree(version.documentJson()).get("nodes");
        for (var node : nodes)
            if (activity.nodeKey().equals(node.path("key").asText())) {
                var outcomes = new java.util.ArrayList<Map<String, Object>>();
                for (var outcome : node.get("outcomes")) {
                    outcomes.add(
                            Map.of(
                                    "key",
                                    outcome.get("key").textValue(),
                                    "label",
                                    outcome.get("label").textValue(),
                                    "requiresComment",
                                    outcome.get("requiresComment").booleanValue()));
                }
                return wire.response(
                        Map.of(
                                "taskId",
                                task.id(),
                                "instanceId",
                                task.instanceId(),
                                "instanceLockVersion",
                                instance.lockVersion(),
                                "taskType",
                                node.get("taskType").textValue(),
                                "outcomes",
                                outcomes));
            }
        throw new IllegalStateException("Pinned human contract missing");
    }

    @GetMapping("/activities/{id}")
    public ResponseEntity<String> activity(@PathVariable String id) {
        return wire.response(access.activity(decimal(id, false)));
    }

    @GetMapping("/activities/{id}/submissions")
    public ResponseEntity<String> submissions(@PathVariable String id, HttpServletRequest r) {
        query(r, "afterTaskId", "limit");
        long key = decimal(id, false);
        access.activity(key);
        var page =
                reads.listActivitySubmissions(
                        key, after(r.getParameter("afterTaskId")), limit(r.getParameter("limit")));
        return wire.response(
                wire.page(
                        page.items(),
                        page.nextAfterTaskId(),
                        "nextAfterTaskId",
                        TaskSubmission::taskId));
    }
}
