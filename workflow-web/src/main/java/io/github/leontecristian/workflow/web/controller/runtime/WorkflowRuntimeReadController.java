package io.github.leontecristian.workflow.web.controller.runtime;

import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.after;
import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.decimal;
import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.limit;
import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.query;

import io.github.leontecristian.workflow.core.dto.runtime.BooleanLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmission;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceQuery;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceSummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowRuntimeReadHost;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeErrors;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeWire;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

/** Registered only by explicit runtime-read activation. */
@ResponseBody
public class WorkflowRuntimeReadController {
    private final WorkflowRuntimeQueryService reads;
    private final WorkflowDefinitionService definitions;
    private final WorkflowRuntimeReadHost access;
    private final RuntimeWire wire;
    private final RuntimeErrors errors;
    public WorkflowRuntimeReadController(WorkflowRuntimeQueryService reads, WorkflowDefinitionService definitions,
            WorkflowRuntimeReadHost access, RuntimeWire wire, RuntimeErrors errors) {
        this.reads = reads; this.definitions = definitions; this.access = access; this.wire = wire; this.errors = errors;
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
                else if(v.value().value() instanceof io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue t) value=t;
            }
            item.put("value",value); items.add(item);
        }
        Map<String,Object> result=new LinkedHashMap<>(); result.put("instanceId",page.instanceId()); result.put("lockVersion",page.lockVersion());
        result.put("items",items); result.put("nextAfterKey",page.nextAfterKey()); return wire.response(result);
    }
    @GetMapping("/instances")
    public ResponseEntity<String> instances(HttpServletRequest r) {
        access.requireMonitorAccess();
        query(r, "status", "afterId", "limit");
        String requested = r.getParameter("status");
        InstanceStatus status = InstanceStatus.ACTIVE;
        if ("ALL".equals(requested)) status = null;
        else if (requested != null) {
            try {
                status = InstanceStatus.valueOf(requested);
            } catch (IllegalArgumentException invalid) {
                throw WorkflowRuntimeException.invalid();
            }
        }
        var page =
                access.listInstances(
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
    @GetMapping("/instances/{id}/tasks")
    public ResponseEntity<String> tasks(@PathVariable String id, HttpServletRequest r) {
        query(r, "afterId", "limit");
        long key = decimal(id, false);

        var page =
                access.listTasks(
                        key, after(r.getParameter("afterId")), limit(r.getParameter("limit")));
        return wire.response(
                wire.page(page.items(), page.nextAfterId(), "nextAfterId", WorkflowTaskView::id));
    }
    @GetMapping("/tasks/mine")
    public ResponseEntity<String> mine(HttpServletRequest r) {
        query(r, "afterId", "limit");
        var page =
                access.listAssignedTasks(

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
                var outcomes = new java.util.ArrayList<io.github.leontecristian.workflow.web.dto.runtime.WorkflowTaskContract.Outcome>();
                for (var outcome : node.get("outcomes")) {
                    outcomes.add(
                            new io.github.leontecristian.workflow.web.dto.runtime.WorkflowTaskContract.Outcome(
                                    outcome.get("key").textValue(), outcome.get("label").textValue(), outcome.get("requiresComment").booleanValue()));
                }
                return wire.response(
                        new io.github.leontecristian.workflow.web.dto.runtime.WorkflowTaskContract(
                                task.id(), task.instanceId(), instance.lockVersion(), node.get("taskType").textValue(), outcomes));
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
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> error(Exception failure, HttpServletRequest request) { return errors.error(failure, request); }
}
