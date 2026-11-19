package io.github.leontecristian.workflow.testhost.controller.runtime;

import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.decimal;
import static io.github.leontecristian.workflow.testhost.service.console.ConsoleWire.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmission;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Host-owned inspection of task evidence under the sandbox administrator grant. */
@RestController
@RequestMapping("/console/api/tasks")
@ConditionalOnProperty(name = "workflow.console.enabled", havingValue = "true")
public class ConsoleTaskDiagnosticsController {
    private final ConsoleContext identity;
    private final ConsoleRuntimeAccess access;
    private final WorkflowRuntimeQueryService reads;
    private final WorkflowDefinitionService definitions;
    private final ConsoleWire wire;

    public ConsoleTaskDiagnosticsController(ConsoleContext identity, ConsoleRuntimeAccess access,
            WorkflowRuntimeQueryService reads, WorkflowDefinitionService definitions, ConsoleWire wire) {
        this.identity = identity;
        this.access = access;
        this.reads = reads;
        this.definitions = definitions;
        this.wire = wire;
    }

    @GetMapping("/{id}/diagnostics")
    public ResponseEntity<String> diagnostics(@PathVariable String id, HttpServletRequest request) {
        String actor = identity.actor();
        if (!identity.isConsoleAdministrator(actor)) throw ConsoleException.missing();
        query(request);
        long taskId = decimal(id, false);
        var task = access.task(actor, taskId);
        var submission = reads.findTaskSubmission(taskId).orElse(null);
        if (submission != null && task.status() != TaskStatus.COMPLETED) {
            task = access.task(actor, taskId);
            if (task.status() != TaskStatus.COMPLETED) throw ConsoleException.missing();
        }
        return wire.response(new TaskDiagnostics(task, pinnedContract(task), submission));
    }

    private Map<String, Object> pinnedContract(WorkflowTaskView task) {
        var instance = reads.findInstance(task.instanceId()).orElseThrow();
        var activity = reads.findActivity(task.activityId()).orElseThrow();
        var version = definitions.findVersion(instance.definitionVersionId()).orElseThrow();
        for (JsonNode node : wire.tree(version.documentJson()).path("nodes")) {
            if (!activity.nodeKey().equals(node.path("key").textValue())) continue;
            var contract = new LinkedHashMap<String, Object>();
            contract.put("instanceLockVersion", instance.lockVersion());
            contract.put("definitionVersionId", instance.definitionVersionId());
            contract.put("nodeKey", activity.nodeKey());
            contract.put("claimPolicy", reads.taskClaimPolicy(task.instanceId(), task.id()));
            contract.put("previousDecisions", reads.previousDecisionsPolicy(task.id()));
            for (String field : new String[] { "taskType", "completion", "outcomes", "resultContract", "inputs" }) {
                if (node.has(field)) contract.put(field, node.get(field));
            }
            contract.putIfAbsent("resultContract", JsonNodeFactory.instance.objectNode().put("mode", "FORBIDDEN"));
            return contract;
        }
        throw new IllegalStateException("Pinned human contract is unavailable");
    }

    private record TaskDiagnostics(WorkflowTaskView task, Map<String, Object> contract, TaskSubmission submission) {}
}
