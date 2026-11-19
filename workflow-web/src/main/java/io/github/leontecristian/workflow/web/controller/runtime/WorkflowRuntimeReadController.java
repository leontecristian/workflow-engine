package io.github.leontecristian.workflow.web.controller.runtime;

import static io.github.leontecristian.workflow.web.service.WorkflowRequestActors.requireActor;
import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.after;
import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.decimal;
import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.limit;
import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.query;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.BooleanLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.TaskClaimPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmission;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmissionPage;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceQuery;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceSummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationQuery;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.web.dto.runtime.WorkflowTaskContract;
import io.github.leontecristian.workflow.web.dto.runtime.WorkflowResultForm;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowAuthorizedRuntimeReadService;
import io.github.leontecristian.workflow.web.integration.WorkflowRequestIdentity;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowAssignmentDirectory;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeErrors;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeWire;
import io.github.leontecristian.workflow.web.service.runtime.WorkflowTaskProjection;
import io.github.leontecristian.workflow.web.service.runtime.WorkflowTaskTokens;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final TaskTypeCatalogService catalog;
    private final WorkflowAuthorizedRuntimeReadService access;
    private final WorkflowRequestIdentity identity;
    private final WorkflowAssignmentDirectory directory;
    private final Clock clock;
    private final RuntimeWire wire;
    private final RuntimeErrors errors;
    private final boolean commandsAvailable;
    private final WorkflowTaskTokens tokens;
    public WorkflowRuntimeReadController(WorkflowRuntimeQueryService reads, WorkflowDefinitionService definitions, TaskTypeCatalogService catalog,
            WorkflowAuthorizedRuntimeReadService access, WorkflowRequestIdentity identity,
            WorkflowAssignmentDirectory directory, Clock clock,
            RuntimeWire wire, RuntimeErrors errors, boolean commandsAvailable) {
        this(reads, definitions, catalog, access, identity, directory, clock, wire, errors, commandsAvailable,
                new WorkflowTaskTokens(null));
    }

    public WorkflowRuntimeReadController(WorkflowRuntimeQueryService reads, WorkflowDefinitionService definitions,
            TaskTypeCatalogService catalog, WorkflowAuthorizedRuntimeReadService access, WorkflowRequestIdentity identity,
            WorkflowAssignmentDirectory directory, Clock clock, RuntimeWire wire, RuntimeErrors errors,
            boolean commandsAvailable, WorkflowTaskTokens tokens) {
        this.reads = reads; this.definitions = definitions; this.access = access; this.wire = wire; this.errors = errors;
        this.commandsAvailable = commandsAvailable;
        this.catalog = catalog; this.identity = identity; this.directory = directory; this.clock = clock;
        this.tokens = tokens;
    }
    @GetMapping("/instances/{id}/variables")
    public ResponseEntity<String> variables(@PathVariable String id,HttpServletRequest r) {
        query(r,"afterKey","limit","expectedLockVersion"); long key=decimal(id,false); access.instance(requireActor(identity), key);
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

    @GetMapping("/instances/{id}/cancellation-availability")
    public ResponseEntity<String> cancellationAvailability(@PathVariable String id, HttpServletRequest request) {
        query(request);
        String actorRef = requireActor(identity);
        var instance = access.instance(actorRef, decimal(id, false));
        boolean allowed = false;
        if (instance.status() == InstanceStatus.ACTIVE && clock != null && access.hasCancellationAuthorization()) {
            Instant time = clock.instant();
            var context = new WorkflowCommandContext(ActorKind.USER, actorRef,
                    Instant.ofEpochSecond(time.getEpochSecond(), time.getNano() / 100 * 100L), null);
            allowed = access.canCancelWorkflow(actorRef, instance, context);
        }
        return wire.response(Map.of("allowed", allowed, "lockVersion", instance.lockVersion()));
    }

    private static Object wireValue(WorkflowValue value) {
        if (value.value() instanceof TextLiteral text) return text.value();
        if (value.value() instanceof BooleanLiteral bool) return bool.value();
        if (value.value() instanceof WorkflowJsonValue json) return json;
        return null;
    }
    @GetMapping("/instances")
    public ResponseEntity<String> instances(HttpServletRequest r) {
        String actorRef = requireActor(identity);
        access.requireMonitorAccess(actorRef);
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
                access.listInstances(actorRef,
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
        query(r, "cursor", "limit");
        long instanceId = decimal(id, false);
        String actorRef = requireActor(identity);
        long afterId = tokens.taskPageAfter("instance", r.getParameter("cursor"), actorRef, instanceId);
        var page = access.listInstanceTasks(actorRef, instanceId, afterId, limit(r.getParameter("limit")));
        var projection = projection(actorRef);
        var items = page.items().stream().map(task -> projection.task(task, true)).toList();
        return taskPage(items, page.nextAfterId(), "instance", actorRef, instanceId);
    }
    @GetMapping("/tasks/mine")
    public ResponseEntity<String> mine(HttpServletRequest r) {
        query(r, "cursor", "limit");
        String actorRef = requireActor(identity);
        long afterId = tokens.taskPageAfter("mine", r.getParameter("cursor"), actorRef, 0);
        var page = access.listMyTasks(actorRef, afterId, limit(r.getParameter("limit")));
        var projection = projection(actorRef);
        var items = page.items().stream().map(task -> projection.task(task, true)).toList();
        return taskPage(items, page.nextAfterId(), "mine", actorRef, 0);
    }
    @GetMapping("/tasks/administration")
    public ResponseEntity<String> administrationTasks(HttpServletRequest request) {
        String actorRef = requireActor(identity);
        access.requireTaskAdministrationAccess(actorRef);
        query(request, "status", "taskType", "assignmentKind", "assignmentRef", "assigneeRef", "instanceId", "cursor", "limit", "taskName");
        TaskStatus status = TaskStatus.OPEN;
        String requested = request.getParameter("status");
        if ("ALL".equals(requested)) status = null;
        else if (requested != null) {
            try { status = TaskStatus.valueOf(requested); }
            catch (IllegalArgumentException invalid) { throw WorkflowRuntimeException.invalid(); }
        }
        String kindValue = request.getParameter("assignmentKind");
        AssignmentTargetKind kind = kindValue == null ? null : assignmentKind(kindValue);
        String ref = filter(request, "assignmentRef", 200);
        if (ref != null && kind == null) throw WorkflowRuntimeException.invalid();
        Long instanceId = request.getParameter("instanceId") == null
                ? null : decimal(request.getParameter("instanceId"), false);
        long scopeId = instanceId == null ? 0 : instanceId;
        long afterId = tokens.taskPageAfter("administration", request.getParameter("cursor"), actorRef, scopeId);
        var page = access.listAdministrationTasks(actorRef, new WorkflowTaskAdministrationQuery(status,
                filter(request, "taskType", 100), kind, ref, filter(request, "assigneeRef", 200), instanceId,
                afterId, limit(request.getParameter("limit")), filter(request, "taskName", 200)));
        var projection = projection(actorRef);
        var items = page.items().stream().map(item -> projection.administrationTask(item)).toList();
        return taskPage(items, page.nextAfterId(), "administration", actorRef, scopeId);
    }

    @GetMapping("/tasks/{id}/reassignment-availability")
    public ResponseEntity<String> reassignmentAvailability(@PathVariable String id, HttpServletRequest request) {
        query(request);
        long taskId = decimal(id, false);
        String actorRef = requireActor(identity);
        var first = access.task(actorRef, taskId);
        // Read the aggregate token before the final authorized task snapshot.
        var instance = reads.findInstance(first.instanceId()).orElseThrow();
        var task = access.task(actorRef, taskId);
        var activity = reads.findActivity(task.activityId()).orElseThrow();
        boolean allowed = canReassign(actorRef, task, instance, activity);
        return wire.response(allowed ? Map.of("allowed", true, "precondition",
                tokens.precondition(actorRef, task.id(), instance.lockVersion())) : Map.of("allowed", false));
    }

    private boolean canReassign(String actorRef, WorkflowTaskView task,
            io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView instance,
            io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView activity) {
        return commandsAvailable && task.status() == TaskStatus.OPEN && instance.status() == InstanceStatus.ACTIVE
                && activity.nodeKey().equals(instance.currentNodeKey()) && access.canReassignTask(actorRef, task);
    }

    @GetMapping("/tasks/{id}/reassignment-targets")
    public ResponseEntity<String> reassignmentTargets(@PathVariable String id, HttpServletRequest request) {
        query(request, "kind", "query", "cursor", "limit");
        String actorRef = requireActor(identity);
        var task = access.task(actorRef, decimal(id, false));
        if (!commandsAvailable || !access.canReassignTask(actorRef, task))
            throw new WorkflowRuntimeException(403, "REASSIGNMENT_FORBIDDEN");
        var kind = assignmentKind(request.getParameter("kind"));
        String search = request.getParameter("query");
        if (search == null) search = "";
        if (search.length() > 200 || !RuntimeWire.unicode(search)) throw WorkflowRuntimeException.invalid();
        String cursor = request.getParameter("cursor");
        if (cursor != null && (cursor.length() > 1000 || !RuntimeWire.unicode(cursor))) throw WorkflowRuntimeException.invalid();
        long count = request.getParameter("limit") == null ? 20 : decimal(request.getParameter("limit"), false);
        if (count > 50) throw WorkflowRuntimeException.invalid();
        var page = directory.reassignmentTargets(new io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext(task, actorRef), kind, search, cursor, (int) count);
        if (page == null || page.items().size() > count || page.nextCursor() != null
                && (page.nextCursor().length() > 1000 || !RuntimeWire.unicode(page.nextCursor())))
            throw new WorkflowRuntimeException(500, "HOST_CONFIGURATION");
        for (var target : page.items()) {
            if (target.ref() == null || target.ref().isBlank() || target.ref().length() > 200 || !RuntimeWire.unicode(target.ref())
                    || target.label() == null || target.label().isBlank() || target.label().length() > 200 || !RuntimeWire.unicode(target.label()))
                throw new WorkflowRuntimeException(500, "HOST_CONFIGURATION");
        }
        return wire.response(page);
    }

    private static AssignmentTargetKind assignmentKind(String value) {
        if (value == null) throw WorkflowRuntimeException.invalid();
        try { return AssignmentTargetKind.valueOf(value); }
        catch (IllegalArgumentException invalid) { throw WorkflowRuntimeException.invalid(); }
    }

    private static String filter(HttpServletRequest request, String key, int bound) {
        String value = request.getParameter(key);
        if (value != null && (value.isBlank() || value.length() > bound || !RuntimeWire.unicode(value)))
            throw WorkflowRuntimeException.invalid();
        return value;
    }
    @GetMapping("/tasks/{id}")
    public ResponseEntity<String> task(@PathVariable String id, HttpServletRequest request) {
        query(request);
        String actorRef = requireActor(identity);
        return wire.response(projection(actorRef).task(access.task(actorRef, decimal(id, false)), false));
    }

    private ResponseEntity<String> taskPage(List<Map<String, Object>> items, Long nextAfterId, String list,
            String actorRef, long scopeId) {
        var result = wire.page(items, nextAfterId, "nextCursor", row -> (Long) row.get("id"));
        if (result.get("nextCursor") instanceof Long position)
            result.put("nextCursor", tokens.taskPageCursor(list, actorRef, scopeId, position));
        return wire.response(result);
    }

    private WorkflowTaskProjection projection(String actorRef) {
        return new WorkflowTaskProjection(reads, access, definitions, catalog, directory, wire, actorRef);
    }
    @GetMapping("/tasks/{id}/submission")
    public ResponseEntity<String> submission(@PathVariable String id) {
        long key = decimal(id, false);
        String actorRef = requireActor(identity);
        var authorized = access.task(actorRef, key);
        var projection = projection(actorRef);
        var submission = reads.findTaskSubmission(key).orElse(null);
        // Open-task ownership can grant a read before completion. Authorize the final task if
        // the submission became visible after the initial grant.
        if (submission != null && authorized.status() != TaskStatus.COMPLETED) {
            authorized = access.task(actorRef, key);
            if (authorized.status() != TaskStatus.COMPLETED)
                throw new WorkflowRuntimeException(404, "NOT_FOUND");
        }
        return wire.response(submission == null ? null : projection.submission(submission, authorized, false));
    }
    @GetMapping("/tasks/{id}/previous-decisions")
    public ResponseEntity<String> previousDecisions(@PathVariable String id, HttpServletRequest r) {
        query(r, "cursor", "limit");
        long key = decimal(id, false);
        String actorRef = requireActor(identity);
        var receivingTask = access.task(actorRef, key);
        long cursor = tokens.after(r.getParameter("cursor"), actorRef, key);
        long count = r.getParameter("limit") == null ? 20 : decimal(r.getParameter("limit"), false);
        if (count > 50) throw WorkflowRuntimeException.invalid();
        var projection = projection(actorRef);
        return wire.previousDecisionPage(reads.listPreviousDecisions(key, cursor, (int) count),
                decision -> projection.previousDecision(decision, receivingTask),
                sequence -> tokens.continuation(actorRef, key, sequence));
    }
    @GetMapping("/tasks/{id}/contract")
    public ResponseEntity<String> contract(@PathVariable String id) {
        long taskId = decimal(id, false);
        String actorRef = requireActor(identity);
        var authorized = access.task(actorRef, taskId);
        // The exact-task grant permits its own pinned contract without general instance access.
        // Read the version before the task snapshot so a stale view cannot carry a newer token.
        var instance = reads.findInstance(authorized.instanceId()).orElseThrow();
        var task = access.task(actorRef, taskId);
        var activity = reads.findActivity(task.activityId()).orElseThrow();
        var claimPolicy = reads.taskClaimPolicy(task.instanceId(), task.id());
        var projection = projection(actorRef);
        var node = projection.node(task, instance.definitionVersionId(), activity.nodeKey());
        var outcomes = new java.util.ArrayList<WorkflowTaskContract.Outcome>();
        for (var outcome : node.path("outcomes")) {
            outcomes.add(new WorkflowTaskContract.Outcome(outcome.path("key").textValue(),
                    outcome.path("label").textValue(), outcome.path("requiresComment").asBoolean()));
        }
        boolean open = task.status() == TaskStatus.OPEN;
        boolean shared = task.assignment().kind() != AssignmentTargetKind.USER;
        boolean assigned = task.assigneeRef() != null;
        boolean claimRequired = open && shared && !assigned && claimPolicy == TaskClaimPolicy.REQUIRED;
        boolean active = commandsAvailable && open && instance.status() == InstanceStatus.ACTIVE
                && activity.nodeKey().equals(instance.currentNodeKey());
        boolean canComplete = active && !claimRequired && access.canCompleteTask(actorRef, task);
        boolean canClaim = active && shared && !assigned && access.canClaimTask(actorRef, task);
        boolean canRelease = active && shared && assigned && access.canReleaseTask(actorRef, task);
        boolean canAdminRelease = active && shared && assigned && access.canAdministrativelyReleaseTask(actorRef, task);
        boolean canReassign = active && access.canReassignTask(actorRef, task);
        var resultContract = node.get("resultContract");
        if (resultContract == null) resultContract = JsonNodeFactory.instance.objectNode().put("mode", "FORBIDDEN");
        var inputs = new LinkedHashMap<String, WorkflowTaskContract.Input>();
        reads.taskInputs(task.instanceId(), task.id()).forEach((key, value) ->
                inputs.put(key, new WorkflowTaskContract.Input(value.type(), wireValue(value))));
        String previousMode = reads.previousDecisionsPolicy(taskId).mode().name();
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("task", projection.task(task, node, false));
        contract.put("outcomes", outcomes.stream().map(outcome -> canComplete
                ? Map.of("key", outcome.key(), "label", outcome.label(), "requiresComment", outcome.requiresComment())
                : Map.of("label", outcome.label(), "requiresComment", outcome.requiresComment())).toList());
        contract.put("canComplete", canComplete);
        contract.put("canClaim", canClaim);
        contract.put("canRelease", canRelease);
        if (canAdminRelease) contract.put("canAdminRelease", true);
        contract.put("claimRequired", claimRequired);
        contract.put("inputs", inputs);
        contract.put("previousDecisionsAvailable", !"NONE".equals(previousMode));
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("mode", resultContract.path("mode").textValue());
        if (canComplete && resultContract.has("schema")) form.put("form", WorkflowResultForm.fromSchema(resultContract.get("schema")));
        contract.put("resultContract", form);
        String type = node.path("taskType").textValue();
        if (canComplete) contract.put("taskType", type);
        if (canComplete || canClaim || canRelease || canAdminRelease || canReassign)
            contract.put("precondition", tokens.precondition(actorRef, task.id(), instance.lockVersion()));
        return wire.largeResponse(contract);
    }
    @GetMapping("/activities/{id}")
    public ResponseEntity<String> activity(@PathVariable String id) {
        return wire.response(access.activity(requireActor(identity), decimal(id, false)));
    }
    @GetMapping("/activities/{id}/submissions")
    public ResponseEntity<String> submissions(@PathVariable String id, HttpServletRequest r) {
        query(r, "afterTaskId", "limit");
        long key = decimal(id, false);
        String actorRef = requireActor(identity);
        access.activity(actorRef, key);
        var visibleTasks = new LinkedHashMap<Long, WorkflowTaskView>();
        var projection = projection(actorRef);
        var page = visibleSubmissions(actorRef, key, after(r.getParameter("afterTaskId")), limit(r.getParameter("limit")),
                visibleTasks);
        return wire.submissionPage(page, submission -> projection.submission(submission, visibleTasks.get(submission.taskId()), true));
    }
    private TaskSubmissionPage visibleSubmissions(String actorRef, long activityId, long after, int limit,
            Map<Long, WorkflowTaskView> visibleTasks) {
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
                if (access.canReadTask(actorRef, task)) {
                    visible.add(submission);
                    visibleTasks.put(task.id(), task);
                }
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
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> error(Exception failure, HttpServletRequest request) { return errors.error(failure, request); }
}
