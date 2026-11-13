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
    private static final int MAX_INPUT_BYTES = 10 * 1024 * 1024;
    private RuntimeTaskCreationEvidence() { }

    static ObjectNode capture(Map<String, HumanInput> inputs, Function<HumanInput, WorkflowValue> resolve, RuntimeJson json) {
        var snapshot = json.object();
        long chars = 2, bytes = 2;
        for (var entry : inputs.entrySet()) {
            var key = entry.getKey(); var input = entry.getValue(); var value = resolve.apply(input);
            String encoded = value.value() instanceof WorkflowJsonValue structured ? structured.toJson()
                    : json.encode(RuntimeValueBindings.token(value.value()));
            String prefix = "{\"type\":\"" + input.type().name() + "\",\"value\":";
            long wrapper = key.length() + 3L + (snapshot.isEmpty() ? 0 : 1) + prefix.length() + 1;
            chars += wrapper + encoded.length(); bytes += wrapper + encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (chars * 2L > RuntimeJson.MAP_UTF16_BYTES || bytes > RuntimeJson.MAP_UTF8_BYTES)
                throw WorkflowExecutionException.resultLimit("The complete human input snapshot exceeds its capacity.");
            var token = RuntimeValueBindings.token(value.value()); validate(input,value,token);
            var typed = snapshot.putObject(key); typed.put("type",input.type().name()); typed.set("value",token);
        }
        return snapshot;
    }

    static Map<String, WorkflowValue> read(WorkflowHistoryEntity history, WorkflowTaskEntity task,
            WorkflowActivityEntity activity, Map<String, HumanInput> inputs, RuntimeJson json) {
        return read(history,task,activity,inputs,json,null);
    }

    static Map<String, WorkflowValue> read(WorkflowHistoryEntity history, WorkflowTaskEntity task,
            WorkflowActivityEntity activity, Map<String, HumanInput> inputs, RuntimeJson json,
            io.github.leontecristian.workflow.core.repository.runtime.WorkflowHistoryRepository repository) {
        RuntimeInputs.require("TASK_CREATED".equals(history.getEventType()) && task.getId().equals(history.getTaskId())
                && activity.getId().equals(history.getActivityId()) && activity.getId().equals(task.getActivityId())
                && activity.getInstanceId().equals(history.getId().getInstanceId())
                && history.getId().getSequenceNumber() > activity.getActivationSequence(),
                Category.HOST_CONFIGURATION);
        short version = history.getDetailsSchemaVersion();
        RuntimeInputs.require(version >= 2 && version <= 6, Category.UNSUPPORTED_VERSION);
        var evidence = json.tree(history.getDetailsJson());
        RuntimeInputs.require(matches(evidence, version, history, task)
                && ((version >= INPUTS_VERSION) == !inputs.isEmpty()), Category.HOST_CONFIGURATION);
        if (inputs.isEmpty()) return Map.of();
        var snapshot = evidence.path("inputs");
        if (version == 6) {
            RuntimeInputs.require(repository != null && activity.getCompletionPolicy() == io.github.leontecristian.workflow.core.dto.runtime.CompletionPolicy.ALL,
                    Category.HOST_CONFIGURATION);
            var facts = repository.findActivityFacts(activity.getId(),"ACTIVITY_INPUTS_CAPTURED",org.springframework.data.domain.PageRequest.of(0,2));
            RuntimeInputs.require(facts.size() == 1, Category.HOST_CONFIGURATION);
            var shared = facts.get(0);
            var entered = repository.findById(new io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryId(activity.getInstanceId(),activity.getActivationSequence()))
                    .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
            RuntimeInputs.require("ACTIVITY_ENTERED".equals(entered.getEventType()) && activity.getId().equals(entered.getActivityId())
                    && entered.getTaskId() == null && entered.getCommandId().equals(shared.getCommandId())
                    && entered.getOccurredAt().equals(shared.getOccurredAt()) && entered.getActorKind() == shared.getActorKind()
                    && entered.getActorRef().equals(shared.getActorRef())
                    && entered.getOccurredAt().equals(activity.getEnteredAt())
                    && "ACTIVITY_INPUTS_CAPTURED".equals(shared.getEventType()) && activity.getId().equals(shared.getActivityId())
                    && shared.getId().getInstanceId().equals(activity.getInstanceId()) && shared.getTaskId() == null
                    && shared.getDetailsSchemaVersion() == 1 && shared.getCommandId().equals(history.getCommandId())
                    && shared.getOccurredAt().equals(history.getOccurredAt()) && shared.getActorKind() == history.getActorKind()
                    && shared.getActorRef().equals(history.getActorRef())
                    && shared.getId().getSequenceNumber() > activity.getActivationSequence()
                    && shared.getId().getSequenceNumber() < history.getId().getSequenceNumber()
                    && Long.toString(shared.getId().getSequenceNumber()).equals(evidence.path("inputSnapshotSequence").textValue()), Category.HOST_CONFIGURATION);
            var siblings = repository.findActivityFacts(activity.getId(),"TASK_CREATED",org.springframework.data.domain.PageRequest.of(0,101));
            RuntimeInputs.require(siblings.size() == activity.getRequiredTaskCount(), Category.HOST_CONFIGURATION);
            var taskIds = new java.util.HashSet<Long>();
            for (var sibling : siblings) {
                var reference = json.tree(sibling.getDetailsJson());
                RuntimeInputs.require(sibling.getDetailsSchemaVersion() == 6 && sibling.getTaskId() != null && taskIds.add(sibling.getTaskId())
                        && sibling.getId().getInstanceId().equals(activity.getInstanceId())
                        && sibling.getCommandId().equals(shared.getCommandId()) && sibling.getOccurredAt().equals(shared.getOccurredAt())
                        && sibling.getActorKind() == shared.getActorKind() && sibling.getActorRef().equals(shared.getActorRef())
                        && sibling.getId().getSequenceNumber() > shared.getId().getSequenceNumber()
                        && reference.path("inputSnapshotSequence").equals(evidence.path("inputSnapshotSequence")), Category.HOST_CONFIGURATION);
            }
            var retained = json.tree(shared.getDetailsJson());
            RuntimeInputs.require(retained.isObject() && retained.size() == 1, Category.HOST_CONFIGURATION);
            snapshot = retained.path("inputs");
        }
        RuntimeInputs.require(snapshot.isObject() && snapshot.size() == inputs.size()
                && json.encode(snapshot).length()*2L <= MAX_INPUT_BYTES, Category.HOST_CONFIGURATION);
        Map<String, WorkflowValue> result = new TreeMap<>();
        var retainedSnapshot = snapshot;
        inputs.forEach((key, input) -> {
            var typed = retainedSnapshot.path(key);
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
                literal = "submission".equals(input.binding().source()) && !input.collection()
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
                && (!"submission".equals(input.binding().source()) || (input.collection() ? token.isArray() : token.isObject())), Category.HOST_CONFIGURATION);
    }

    static boolean matches(WorkflowHistoryEntity history, WorkflowTaskEntity task, RuntimeJson json) {
        return matches(json.tree(history.getDetailsJson()), history.getDetailsSchemaVersion(), history, task);
    }

    private static boolean matches(JsonNode evidence, short version, WorkflowHistoryEntity history, WorkflowTaskEntity task) {
        if (!evidence.isObject() || evidence.size() != (version >= 5 ? 4 : version == 4 ? 3 : 2) || !evidence.path("taskKey").isTextual()
                || !task.getTaskKey().equals(evidence.path("taskKey").textValue())) return false;
        TaskAssignmentTarget target;
        if (version == 2 && evidence.path("assignedUserRef").isTextual()) {
            target = new TaskAssignmentTarget(AssignmentTargetKind.USER, evidence.path("assignedUserRef").textValue());
        } else if (version >= 3 && version <= 6) {
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
