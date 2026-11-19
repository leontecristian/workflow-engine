package io.github.leontecristian.workflow.web.controller.runtime;

import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.decimal;
import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.id;
import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.text;
import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.token;
import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.uuid;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.leontecristian.workflow.core.dto.runtime.AdministrativelyReleaseWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.CancelWorkflow;
import io.github.leontecristian.workflow.core.dto.runtime.ClaimWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ReassignWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ReleaseWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeErrors;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeWire;
import io.github.leontecristian.workflow.web.service.runtime.WorkflowRuntimeCommandCoordinator;
import io.github.leontecristian.workflow.web.service.runtime.WorkflowTaskTokens;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Runtime command transport. Shared coordination delegates execution and receipt access to core. */
@ResponseBody
public class WorkflowRuntimeCommandController {
    private final WorkflowRuntimeCommandCoordinator commands;
    private final RuntimeWire wire;
    private final RuntimeErrors errors;

    public WorkflowRuntimeCommandController(WorkflowRuntimeCommandCoordinator commands, RuntimeWire wire, RuntimeErrors errors) {
        this.commands = commands;
        this.wire = wire;
        this.errors = errors;
    }

    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<String> complete(@PathVariable String taskId, HttpServletRequest request) {
        var body = wire.completionBody(request, "commandId", "outcomeKey", "comment");
        var command = completion(request, decimal(taskId, false), body);
        return wire.response(commands.taskResult(commands.complete(command, errors.correlation(request))));
    }

    @PostMapping("/commands/complete/replay")
    public ResponseEntity<String> replayComplete(HttpServletRequest request) {
        var body = wire.completionBody(request, "commandId", "taskId", "outcomeKey", "comment");
        var command = completion(request, id(body.fields(), "taskId", false), body);
        return wire.response(replay(commands.replayComplete(command, errors.correlation(request))
                .map(commands::taskResult).orElse(null)));
    }

    private CompleteWorkflowTask completion(HttpServletRequest request, long taskId, RuntimeWire.CompletionBody body) {
        var fields = body.fields();
        var commandId = uuid(fields);
        String outcome = text(fields, "outcomeKey", false);
        String comment = text(fields, "comment", true);
        var target = target(request, taskId);
        return new CompleteWorkflowTask(commandId, target.instanceId(), taskId, target.expectedVersion(),
                outcome, comment, body.result());
    }

    @PostMapping("/tasks/{taskId}/claim")
    public ResponseEntity<String> claim(@PathVariable String taskId, HttpServletRequest request) {
        var body = wire.body(request, "commandId");
        var command = claim(request, decimal(taskId, false), body);
        return wire.response(commands.taskResult(commands.claim(command, errors.correlation(request))));
    }

    @PostMapping("/commands/claim/replay")
    public ResponseEntity<String> replayClaim(HttpServletRequest request) {
        var body = wire.body(request, "commandId", "taskId");
        var command = claim(request, id(body, "taskId", false), body);
        return wire.response(replay(commands.replayClaim(command, errors.correlation(request))
                .map(commands::taskResult).orElse(null)));
    }

    private ClaimWorkflowTask claim(HttpServletRequest request, long taskId, JsonNode body) {
        var target = target(request, taskId);
        return new ClaimWorkflowTask(uuid(body), target.instanceId(), taskId, target.expectedVersion());
    }

    @PostMapping("/tasks/{taskId}/release")
    public ResponseEntity<String> release(@PathVariable String taskId, HttpServletRequest request) {
        var body = wire.body(request, "commandId");
        var command = release(request, decimal(taskId, false), body);
        return wire.response(commands.taskResult(commands.release(command, errors.correlation(request))));
    }

    @PostMapping("/commands/release/replay")
    public ResponseEntity<String> replayRelease(HttpServletRequest request) {
        var body = wire.body(request, "commandId", "taskId");
        var command = release(request, id(body, "taskId", false), body);
        return wire.response(replay(commands.replayRelease(command, errors.correlation(request))
                .map(commands::taskResult).orElse(null)));
    }

    private ReleaseWorkflowTask release(HttpServletRequest request, long taskId, JsonNode body) {
        var target = target(request, taskId);
        return new ReleaseWorkflowTask(uuid(body), target.instanceId(), taskId, target.expectedVersion());
    }

    @PostMapping("/tasks/{taskId}/admin-release")
    public ResponseEntity<String> administrativelyRelease(@PathVariable String taskId, HttpServletRequest request) {
        var body = wire.body(request, "commandId", "reason");
        var command = administrativeRelease(request, decimal(taskId, false), body);
        return wire.response(commands.taskResult(commands.administrativelyRelease(command, errors.correlation(request))));
    }

    @PostMapping("/commands/admin-release/replay")
    public ResponseEntity<String> replayAdministrativeRelease(HttpServletRequest request) {
        var body = wire.body(request, "commandId", "taskId", "reason");
        var command = administrativeRelease(request, id(body, "taskId", false), body);
        return wire.response(replay(commands.replayAdministrativeRelease(command, errors.correlation(request))
                .map(commands::taskResult).orElse(null)));
    }

    private AdministrativelyReleaseWorkflowTask administrativeRelease(HttpServletRequest request, long taskId, JsonNode body) {
        var commandId = uuid(body);
        String reason = reason(body, 1000);
        var target = target(request, taskId);
        return new AdministrativelyReleaseWorkflowTask(commandId, target.instanceId(), taskId, target.expectedVersion(), reason);
    }

    @PostMapping("/tasks/{taskId}/reassign")
    public ResponseEntity<String> reassign(@PathVariable String taskId, HttpServletRequest request) {
        var body = wire.body(request, "commandId", "assignment", "reason");
        var command = reassignment(request, decimal(taskId, false), body);
        return wire.response(commands.taskResult(commands.reassign(command, errors.correlation(request))));
    }

    @PostMapping("/commands/reassign/replay")
    public ResponseEntity<String> replayReassignment(HttpServletRequest request) {
        var body = wire.body(request, "commandId", "taskId", "assignment", "reason");
        var command = reassignment(request, id(body, "taskId", false), body);
        return wire.response(replay(commands.replayReassignment(command, errors.correlation(request))
                .map(commands::taskResult).orElse(null)));
    }

    private ReassignWorkflowTask reassignment(HttpServletRequest request, long taskId, JsonNode body) {
        var commandId = uuid(body);
        var assignment = assignment(body);
        String reason = reason(body, 1000);
        var target = target(request, taskId);
        return new ReassignWorkflowTask(commandId, target.instanceId(), taskId, target.expectedVersion(), assignment, reason);
    }

    private static TaskAssignmentTarget assignment(JsonNode body) {
        var target = body.get("assignment");
        if (target == null || !target.isObject() || target.size() != 2
                || !target.has("kind") || !target.has("ref")) throw WorkflowRuntimeException.invalid();
        AssignmentTargetKind kind;
        try { kind = AssignmentTargetKind.valueOf(text(target, "kind", false)); }
        catch (IllegalArgumentException invalid) { throw WorkflowRuntimeException.invalid(); }
        String ref = text(target, "ref", false);
        if (ref.isBlank() || ref.length() > 200) throw WorkflowRuntimeException.invalid();
        return new TaskAssignmentTarget(kind, ref);
    }

    @PostMapping("/instances/{instanceId}/cancel")
    public ResponseEntity<String> cancel(@PathVariable String instanceId, HttpServletRequest request) {
        var body = wire.body(request, "commandId", "reason");
        var command = new CancelWorkflow(uuid(body), decimal(instanceId, false), token(request), reason(body, 2000));
        return wire.response(commands.cancel(command, errors.correlation(request)));
    }

    @PostMapping("/commands/cancel/replay")
    public ResponseEntity<String> replayCancellation(HttpServletRequest request) {
        var body = wire.body(request, "commandId", "instanceId", "reason");
        var command = new CancelWorkflow(uuid(body), id(body, "instanceId", false), token(request), reason(body, 2000));
        return wire.response(replay(commands.replayCancellation(command, errors.correlation(request)).orElse(null)));
    }

    private WorkflowRuntimeCommandCoordinator.TaskTarget target(HttpServletRequest request, long taskId) {
        return commands.taskTarget(taskId, request.getHeader(WorkflowTaskTokens.PRECONDITION_HEADER));
    }

    private static String reason(JsonNode body, int maximum) {
        String reason = text(body, "reason", false);
        if (reason.isBlank() || reason.length() > maximum) throw WorkflowRuntimeException.invalid();
        return reason;
    }

    private Map<String, Object> replay(Object result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("found", result != null);
        map.put("result", result);
        return map;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> error(Exception failure, HttpServletRequest request) { return errors.error(failure, request); }
}
