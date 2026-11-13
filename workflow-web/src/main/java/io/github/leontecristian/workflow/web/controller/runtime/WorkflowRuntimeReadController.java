package io.github.leontecristian.workflow.web.controller.runtime;

import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.after;
import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.decimal;
import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.limit;
import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.query;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.BooleanLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.TaskClaimPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmission;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmissionPage;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceQuery;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceSummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.web.dto.runtime.WorkflowTaskContract;
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
    private final boolean commandsAvailable;
    public WorkflowRuntimeReadController(WorkflowRuntimeQueryService reads, WorkflowDefinitionService definitions,
            WorkflowRuntimeReadHost access, RuntimeWire wire, RuntimeErrors errors, boolean commandsAvailable) {
        this.reads = reads; this.definitions = definitions; this.access = access; this.wire = wire; this.errors = errors;
        this.commandsAvailable = commandsAvailable;
    }
    @GetMapping("/instances/{id}/variables")
    public ResponseEntity<String> variables(@PathVariable String id,HttpServletRequest r) {
        query(r,"afterKey","limit","expectedLockVersion"); long key=decimal(id,false); access.instance(key);
        var page=reads.listVariables(key,r.getParameter("afterKey"),limit(r.getParameter("limit")),
                r.getParameter("expectedLockVersion")==null ? null : decimal(r.getParameter("expectedLockVersion"),true));
        var items=new java.util.ArrayList<Map<String,Object>>();
        for(var v:page.items()) {
            Map<String,Object> item=new LinkedHashMap<>(); item.put("key",v.key()); item.put("type",v.type()); item.put("initialized",v.initialized());
            item.put("value", v.value() == null ? null : wireValue(v.value())); items.add(item);
        }
        Map<String,Object> result=new LinkedHashMap<>(); result.put("instanceId",page.instanceId()); result.put("lockVersion",page.lockVersion());
        result.put("items",items); result.put("nextAfterKey",page.nextAfterKey()); return wire.largeResponse(result);
    }

    private static Object wireValue(WorkflowValue value) {
        if (value.value() instanceof TextLiteral text) return text.value();
        if (value.value() instanceof BooleanLiteral bool) return bool.value();
        if (value.value() instanceof WorkflowJsonValue json) return json;
        return null;
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
        var authorized = access.task(key);
        var submission = reads.findTaskSubmission(key).orElse(null);
        // Open-task ownership can grant a read before completion. Authorize the final task if
        // the submission became visible after the initial grant.
        if (submission != null && authorized.status() != TaskStatus.COMPLETED
                && access.task(key).status() != TaskStatus.COMPLETED) {
            throw new WorkflowRuntimeException(404, "NOT_FOUND");
        }
        return wire.response(submission);
    }
    @GetMapping("/tasks/{id}/contract")
    public ResponseEntity<String> contract(@PathVariable String id) {
        long taskId = decimal(id, false);
        var authorized = access.task(taskId);
        // The exact-task grant permits its own pinned contract without general instance access.
        // Read the version before the task snapshot so a stale view cannot carry a newer token.
        var instance = reads.findInstance(authorized.instanceId()).orElseThrow();
        var task = access.task(taskId);
        var activity = reads.findActivity(task.activityId()).orElseThrow();
        var claimPolicy = reads.taskClaimPolicy(task.instanceId(), task.id());
        var version =
                definitions
                        .findVersion(instance.definitionVersionId())
                        .orElseThrow(() -> new IllegalStateException("Pinned definition missing"));
        var nodes = wire.tree(version.documentJson()).get("nodes");
        for (var node : nodes)
            if (activity.nodeKey().equals(node.path("key").asText())) {
                var outcomes = new java.util.ArrayList<WorkflowTaskContract.Outcome>();
                for (var outcome : node.get("outcomes")) {
                    outcomes.add(
                            new WorkflowTaskContract.Outcome(
                                    outcome.get("key").textValue(), outcome.get("label").textValue(), outcome.get("requiresComment").booleanValue()));
                }
                boolean open = task.status() == TaskStatus.OPEN;
                boolean shared = task.assignment().kind() != AssignmentTargetKind.USER;
                boolean assigned = task.assigneeRef() != null;
                boolean canComplete = open && (!shared || assigned || claimPolicy == TaskClaimPolicy.OPTIONAL)
                        && access.canCompleteTask(task);
                var resultContract = node.get("resultContract");
                if (resultContract == null) resultContract = JsonNodeFactory.instance.objectNode().put("mode", "FORBIDDEN");
                var inputs = new LinkedHashMap<String, WorkflowTaskContract.Input>();
                reads.taskInputs(task.instanceId(), task.id()).forEach((key, value) ->
                        inputs.put(key, new WorkflowTaskContract.Input(value.type(), wireValue(value))));
                return wire.largeResponse(new WorkflowTaskContract(
                        task.id(), task.instanceId(), instance.lockVersion(), node.get("taskType").textValue(),
                        outcomes, canComplete, assignmentLabel(task), claimPolicy,
                        commandsAvailable && open && shared && !assigned && access.canClaimTask(task),
                        commandsAvailable && open && shared && assigned && access.canReleaseTask(task),
                        commandsAvailable && open && shared && assigned && access.canAdministrativelyReleaseTask(task),
                        resultContract, inputs));
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
        var page = visibleSubmissions(key, after(r.getParameter("afterTaskId")), limit(r.getParameter("limit")));
        return wire.submissionPage(page);
    }
    private TaskSubmissionPage visibleSubmissions(long activityId, long after, int limit) {
        var visible = new java.util.ArrayList<TaskSubmission>();
        long cursor = after;
        int examined = 0;
        while (examined < 500) {
            var page = reads.listActivitySubmissions(activityId, cursor, Math.min(limit, 500 - examined));
            for (int index = 0; index < page.items().size(); index++) {
                var submission = page.items().get(index);
                cursor = submission.taskId(); examined++;
                var task = reads.findTask(submission.taskId())
                        .orElseThrow(() -> new IllegalStateException("Submission task missing"));
                if (access.canReadTask(task)) visible.add(submission);
                if (visible.size() == limit || examined == 500) {
                    boolean more = index + 1 < page.items().size() || page.nextAfterTaskId() != null;
                    return new TaskSubmissionPage(visible, more ? cursor : null);
                }
            }
            if (page.nextAfterTaskId() == null)
                return new TaskSubmissionPage(visible, null);
            cursor = page.nextAfterTaskId();
        }
        return new TaskSubmissionPage(visible, cursor);
    }
    private String assignmentLabel(WorkflowTaskView task) {
        String label;
        try { label = access.assignmentLabel(task); }
        catch (RuntimeException unavailable) { label = null; }
        if (label == null || label.isBlank() || !RuntimeWire.unicode(label) || label.length() > 200)
            return task.assignment().ref();
        return label;
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> error(Exception failure, HttpServletRequest request) { return errors.error(failure, request); }
}
