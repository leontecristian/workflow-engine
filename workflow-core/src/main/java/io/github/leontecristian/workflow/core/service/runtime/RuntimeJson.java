package io.github.leontecristian.workflow.core.service.runtime;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.StartWorkflow;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowExecutionResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowOperation;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowCommandReceiptEntity;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.service.definition.DefinitionContentCanonicalizer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Private, versioned runtime JSON; never modifies host serialization configuration. */
@Component
public class RuntimeJson {
    static final String FINGERPRINT_VERSION = "wf-runtime-request-v1";
    private final ObjectMapper mapper;
    public RuntimeJson() {
        mapper = new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(131072).maxNumberLength(128).build()).build());
        SimpleModule numbers = new SimpleModule();
        numbers.addSerializer(Long.class, ToStringSerializer.instance);
        numbers.addSerializer(Long.TYPE, ToStringSerializer.instance);
        JsonDeserializer<Long> longs = new JsonDeserializer<>() {
            @Override public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                if (parser.currentToken() != JsonToken.VALUE_STRING || !parser.getText().matches("0|[1-9][0-9]{0,18}"))
                    throw new IOException("Expected decimal Long string");
                try { return Long.valueOf(parser.getText()); }
                catch (NumberFormatException failure) { throw new IOException("Long out of range", failure); }
            }
        };
        numbers.addDeserializer(Long.class, longs); numbers.addDeserializer(Long.TYPE, longs);
        JsonDeserializer<Integer> integers = new JsonDeserializer<>() {
            @Override public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                if (!parser.currentToken().isNumeric()) throw new IOException("Expected exact integer number");
                try { return parser.getDecimalValue().intValueExact(); }
                catch (ArithmeticException failure) { throw new IOException("Integer out of range", failure); }
            }
        };
        numbers.addDeserializer(Integer.class, integers); numbers.addDeserializer(Integer.TYPE, integers);
        mapper.registerModule(new JavaTimeModule()).registerModule(numbers);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
                DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
    }
    ObjectNode object() { return mapper.createObjectNode(); }
    String encode(Object value) { return DefinitionContentCanonicalizer.canonical(mapper.valueToTree(value)); }
    static byte[] hash(String value) { return DefinitionContentCanonicalizer.hash(value); }
    static boolean fits(String text, int bytes) {
        return text != null && text.length() * 2L <= bytes && text.getBytes(StandardCharsets.UTF_8).length <= bytes;
    }
    String evidence(Object value) {
        String json = encode(value); RuntimeInputs.require(fits(json, 128 * 1024), Category.LIMIT_EXCEEDED); return json;
    }
    JsonNode tree(String source) {
        RuntimeInputs.require(fits(source, 128 * 1024) && RuntimeInputs.unicode(source), Category.HOST_CONFIGURATION);
        try { return mapper.readTree(source); }
        catch (IOException failure) { throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure); }
    }
    byte[] fingerprint(StartWorkflow command, WorkflowCommandContext context) {
        ObjectNode request = object(); request.put("definitionVersionId", Long.toString(command.definitionVersionId()));
        request.put("businessType", command.businessType()); request.put("businessId", command.businessId());
        if (command.hostStartIntent() == null) request.putNull("hostStartIntent");
        else request.set("hostStartIntent", mapper.valueToTree(command.hostStartIntent()));
        return fingerprint(WorkflowOperation.START_WORKFLOW, context, request);
    }
    byte[] fingerprint(CompleteWorkflowTask command, WorkflowCommandContext context) {
        ObjectNode request = object(); request.put("instanceId", Long.toString(command.instanceId()));
        request.put("taskId", Long.toString(command.taskId())); request.put("expectedLockVersion", Long.toString(command.expectedLockVersion()));
        request.put("outcomeKey", command.outcomeKey()); request.put("comment", command.comment());
        return fingerprint(WorkflowOperation.COMPLETE_TASK, context, request);
    }
    private byte[] fingerprint(WorkflowOperation operation, WorkflowCommandContext context, ObjectNode request) {
        ObjectNode root = object(); root.put("version", FINGERPRINT_VERSION); root.put("operation", operation.name());
        root.put("actorKind", context.actorKind().name()); root.put("actorRef", context.actorRef()); root.set("request", request);
        return hash(DefinitionContentCanonicalizer.canonical(root));
    }
    Optional<WorkflowExecutionResult> replay(WorkflowCommandReceiptEntity receipt, UUID commandId,
            WorkflowOperation operation, WorkflowCommandContext context, byte[] fingerprint) {
        if (receipt == null) return Optional.empty();
        RuntimeInputs.require(receipt.getActorKind() == context.actorKind() && java.util.Objects.equals(receipt.getActorRef(), context.actorRef())
                && operation.name().equals(receipt.getOperationType()), Category.COMMAND_ID_CONFLICT);
        RuntimeInputs.require(FINGERPRINT_VERSION.equals(receipt.getFingerprintVersion()) && Short.valueOf((short) 1).equals(receipt.getResultSchemaVersion()),
                Category.UNSUPPORTED_VERSION);
        RuntimeInputs.require(Arrays.equals(receipt.getRequestFingerprint(), fingerprint), Category.COMMAND_ID_CONFLICT);
        JsonNode stored = tree(receipt.getResultJson());
        requireTimeStrings(stored);
        try {
            WorkflowExecutionResult result = mapper.readValue(receipt.getResultJson(), WorkflowExecutionResult.class);
            verifyResult(result, commandId, operation, receipt);
            return Optional.of(result);
        } catch (IOException | IllegalArgumentException failure) { throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure); }
    }
    private void verifyResult(WorkflowExecutionResult result, UUID commandId, WorkflowOperation operation, WorkflowCommandReceiptEntity receipt) {
        RuntimeInputs.require(result != null && commandId.equals(result.commandId()) && result.operation() == operation
                && result.instance() != null && result.activity() != null && result.task() != null, Category.HOST_CONFIGURATION);
        var instance = result.instance(); var activity = result.activity(); var task = result.task();
        RuntimeInputs.require(instance.id() == receipt.getInstanceId() && instance.id() > 0 && instance.definitionVersionId() > 0 && instance.lockVersion() >= 0
                && activity.instanceId() == instance.id() && task.instanceId() == instance.id() && task.activityId() == activity.id()
                && task.taskTypeId() == activity.taskTypeId() && instance.lastHistorySequence() == result.lastHistorySequence()
                && result.firstHistorySequence() == (operation == WorkflowOperation.START_WORKFLOW ? 1 : 5)
                && result.lastHistorySequence() == (operation == WorkflowOperation.START_WORKFLOW ? 4 : 8)
                && instance.definitionHash() != null && instance.definitionHash().matches("[0-9a-f]{64}")
                && RuntimeInputs.key(instance.definitionKey()) && instance.definitionVersion() > 0
                && task.id() > 0 && activity.id() > 0, Category.HOST_CONFIGURATION);
        RuntimeInputs.require(RuntimeInputs.key(instance.currentNodeKey()) && validTime(instance.startedAt())
                && RuntimeInputs.text(instance.startedBy(), 200, 200, true) && instance.terminationReason() == null
                && ((instance.businessType() == null && instance.businessId() == null)
                    || (RuntimeInputs.key(instance.businessType()) && RuntimeInputs.text(instance.businessId(), 200, 200, true)))
                && RuntimeInputs.key(activity.nodeKey()) && activity.activationSequence() == 3 && activity.taskTypeId() > 0
                && activity.completionPolicy() == io.github.leontecristian.workflow.core.dto.runtime.CompletionPolicy.SINGLE
                && activity.requiredTaskCount() == 1 && activity.contributionOutcomeKey() == null
                && (activity.reviewRevisionRef() == null || RuntimeInputs.text(activity.reviewRevisionRef(), 200, 200, true))
                && instance.startedAt().equals(activity.enteredAt()) && activity.enteredAt().equals(task.createdAt())
                && RuntimeInputs.key(task.taskKey()) && RuntimeInputs.text(task.assignedUserRef(), 200, 200, true) && task.dueAt() == null,
                Category.HOST_CONFIGURATION);
        if (operation == WorkflowOperation.START_WORKFLOW) {
            RuntimeInputs.require(instance.status() == io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus.ACTIVE
                    && activity.status() == io.github.leontecristian.workflow.core.dto.runtime.ActivityStatus.ACTIVE
                    && task.status() == io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.OPEN
                    && instance.currentNodeKey().equals(activity.nodeKey()) && instance.resultKey() == null
                    && instance.endedAt() == null && instance.endedBy() == null && activity.outcomeKey() == null
                    && activity.closedAt() == null && activity.closedBy() == null && activity.closureReason() == null
                    && task.outcomeKey() == null && task.closedAt() == null && task.closedBy() == null && task.closureReason() == null
                    && instance.startedBy().equals(receipt.getActorRef()) && instance.startedAt().equals(RuntimeViews.instant(receipt.getRecordedAt())),
                    Category.HOST_CONFIGURATION);
        } else {
            RuntimeInputs.require(instance.status() == io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus.COMPLETED
                    && activity.status() == io.github.leontecristian.workflow.core.dto.runtime.ActivityStatus.COMPLETED
                    && task.status() == io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.COMPLETED
                    && RuntimeInputs.key(instance.resultKey()) && validTime(instance.endedAt()) && !instance.endedAt().isBefore(instance.startedAt())
                    && instance.endedAt().equals(activity.closedAt()) && activity.closedAt().equals(task.closedAt())
                    && receipt.getActorKind() == io.github.leontecristian.workflow.core.dto.runtime.ActorKind.USER
                    && receipt.getActorRef().equals(instance.endedBy()) && receipt.getActorRef().equals(activity.closedBy())
                    && receipt.getActorRef().equals(task.closedBy()) && receipt.getActorRef().equals(task.assignedUserRef())
                    && "TASK_OUTCOME".equals(activity.closureReason())
                    && task.closureReason() == io.github.leontecristian.workflow.core.dto.runtime.TaskClosureReason.TASK_OUTCOME
                    && RuntimeInputs.key(task.outcomeKey()) && task.outcomeKey().equals(activity.outcomeKey())
                    && instance.endedAt().equals(RuntimeViews.instant(receipt.getRecordedAt())), Category.HOST_CONFIGURATION);
        }
    }
    private boolean validTime(java.time.Instant time) {
        if (time == null || time.getNano() % 100 != 0) return false;
        try { int year = java.time.LocalDateTime.ofInstant(time, java.time.ZoneOffset.UTC).getYear(); return year >= 1 && year <= 9999; }
        catch (java.time.DateTimeException failure) { return false; }
    }
    private void requireTimeStrings(JsonNode value) {
        if (value.isObject()) value.fields().forEachRemaining(field -> {
            if (java.util.Set.of("startedAt", "endedAt", "enteredAt", "closedAt", "createdAt", "dueAt").contains(field.getKey()))
                RuntimeInputs.require(field.getValue().isNull() || field.getValue().isTextual(), Category.HOST_CONFIGURATION);
            requireTimeStrings(field.getValue());
        });
        else if (value.isArray()) value.forEach(this::requireTimeStrings);
    }
}
