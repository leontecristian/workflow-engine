package io.github.leontecristian.workflow.web.controller.runtime;

import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.decimal;
import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.id;
import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.text;
import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.token;
import static io.github.leontecristian.workflow.web.service.runtime.RuntimeWire.uuid;

import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowRuntimeCommandHost;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeErrors;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeWire;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
/** Completion transport only; host owns execution and replay-only recovery. */
@ResponseBody
public class WorkflowRuntimeCommandController {
    private final WorkflowRuntimeCommandHost commands;
    private final RuntimeWire wire;
    private final RuntimeErrors errors;
    public WorkflowRuntimeCommandController(WorkflowRuntimeCommandHost commands, RuntimeWire wire, RuntimeErrors errors) {
        this.commands = commands; this.wire = wire; this.errors = errors;
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
                        errors.correlation(r)));
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

        return wire.response(
                replay(
                        commands.replayComplete(
                                        command, errors.correlation(r))
                                .orElse(null)));
    }
    private Map<String, Object> replay(WorkflowCommandResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("found", result != null);
        map.put("result", result);
        return map;
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> error(Exception failure, HttpServletRequest request) { return errors.error(failure, request); }
}
