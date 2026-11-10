package io.github.leontecristian.workflow.core.service.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.HumanInput;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.BooleanLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.NullLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowActivityEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowTaskEntity;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/** Strict interpretation of retained creation evidence without rewriting historical bytes. */
final class RuntimeTaskCreationEvidence {
    static final short INPUTS_VERSION = 5;
    private static final int MAX_INPUT_BYTES = 128 * 1024;
    private RuntimeTaskCreationEvidence() { }

    static ObjectNode capture(Map<String, HumanInput> inputs, Function<HumanInput, WorkflowValue> resolve, RuntimeJson json) {
        var snapshot = json.object();
        inputs.forEach((key, input) -> {
            var value = resolve.apply(input);
            var token = RuntimeValueBindings.token(value.value());
            validate(input, value, token);
            var typed = snapshot.putObject(key);
            typed.put("type", input.type().name());
            typed.set("value", token);
        });
        RuntimeInputs.require(RuntimeJson.fits(json.encode(snapshot), MAX_INPUT_BYTES), Category.LIMIT_EXCEEDED);
        return snapshot;
    }

    static Map<String, WorkflowValue> read(WorkflowHistoryEntity history, WorkflowTaskEntity task,
            WorkflowActivityEntity activity, Map<String, HumanInput> inputs, RuntimeJson json) {
        RuntimeInputs.require("TASK_CREATED".equals(history.getEventType()) && task.getId().equals(history.getTaskId())
                && activity.getId().equals(history.getActivityId()) && activity.getId().equals(task.getActivityId())
                && activity.getInstanceId().equals(history.getId().getInstanceId())
                && history.getId().getSequenceNumber() == RuntimeProgression.advance(activity.getActivationSequence(), 1),
                Category.HOST_CONFIGURATION);
        short version = history.getDetailsSchemaVersion();
        RuntimeInputs.require(version >= 2 && version <= INPUTS_VERSION, Category.UNSUPPORTED_VERSION);
        var evidence = json.tree(history.getDetailsJson());
        RuntimeInputs.require(matches(evidence, version, history, task)
                && ((version == INPUTS_VERSION) == !inputs.isEmpty()), Category.HOST_CONFIGURATION);
        if (inputs.isEmpty()) return Map.of();
        var snapshot = evidence.path("inputs");
        RuntimeInputs.require(snapshot.isObject() && snapshot.size() == inputs.size()
                && RuntimeJson.fits(json.encode(snapshot), MAX_INPUT_BYTES), Category.HOST_CONFIGURATION);
        Map<String, WorkflowValue> result = new TreeMap<>();
        inputs.forEach((key, input) -> {
            var typed = snapshot.path(key);
            var token = typed.path("value");
            RuntimeInputs.require(typed.isObject() && typed.size() == 2 && !token.isMissingNode()
                    && input.type().name().equals(typed.path("type").textValue()), Category.HOST_CONFIGURATION);
            var value = decode(input, token, json);
            validate(input, value, token);
            result.put(key, value);
        });
        return Map.copyOf(result);
    }

    private static WorkflowValue decode(HumanInput input, JsonNode token, RuntimeJson json) {
        var type = input.type();
        try {
            WorkflowLiteral literal;
            if (token.isNull()) literal = NullLiteral.INSTANCE;
            else if (type == VariableValueType.JSON) {
                var encoded = json.encode(token);
                literal = "submission".equals(input.binding().source())
                        ? CompleteWorkflowTask.parseResult(encoded) : WorkflowJsonValue.parse(encoded);
            }
            else if (token.isBoolean()) literal = new BooleanLiteral(token.booleanValue());
            else if (token.isTextual()) literal = new TextLiteral(token.textValue());
            else throw new IllegalArgumentException("Invalid retained value token");
            var value = new WorkflowValue(type, literal);
            // Retained scalar tokens must already be in the exact form written by the runtime.
            RuntimeInputs.require(type == VariableValueType.JSON || value.value().equals(literal), Category.HOST_CONFIGURATION);
            return value;
        } catch (IllegalArgumentException | java.time.DateTimeException | WorkflowExecutionException failure) {
            throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure);
        }
    }

    private static void validate(HumanInput input, WorkflowValue value, JsonNode token) {
        RuntimeInputs.require(value.type() == input.type() && (input.nullable() || !token.isNull())
                && (!"submission".equals(input.binding().source()) || token.isObject()), Category.HOST_CONFIGURATION);
    }

    static boolean matches(WorkflowHistoryEntity history, WorkflowTaskEntity task, RuntimeJson json) {
        return matches(json.tree(history.getDetailsJson()), history.getDetailsSchemaVersion(), history, task);
    }

    private static boolean matches(JsonNode evidence, short version, WorkflowHistoryEntity history, WorkflowTaskEntity task) {
        if (!evidence.isObject() || evidence.size() != (version >= 4 ? version - 1 : 2) || !evidence.path("taskKey").isTextual()
                || !task.getTaskKey().equals(evidence.path("taskKey").textValue())) return false;
        TaskAssignmentTarget target;
        if (version == 2 && evidence.path("assignedUserRef").isTextual()) {
            target = new TaskAssignmentTarget(AssignmentTargetKind.USER, evidence.path("assignedUserRef").textValue());
        } else if (version >= 3 && version <= INPUTS_VERSION) {
            JsonNode assignment = evidence.path("assignment");
            if (!assignment.isObject() || assignment.size() != 2 || !assignment.path("kind").isTextual()
                    || !assignment.path("ref").isTextual()) return false;
            AssignmentTargetKind kind;
            try { kind = AssignmentTargetKind.valueOf(assignment.path("kind").textValue()); }
            catch (IllegalArgumentException failure) { return false; }
            target = new TaskAssignmentTarget(kind, assignment.path("ref").textValue());
            if (version >= 4) {
                JsonNode assignee = evidence.path("assigneeRef");
                if (kind == AssignmentTargetKind.USER
                        ? !assignee.isTextual() || !target.ref().equals(assignee.textValue())
                        : !assignee.isNull()) return false;
            }
        } else return false;
        return RuntimeInputs.text(target.ref(), 200, 200, true) && target.equals(task.getAssignment())
                && history.getOccurredAt().equals(task.getCreatedAt());
    }
}
