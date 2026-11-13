package io.github.leontecristian.workflow.core.service.runtime;
import io.github.leontecristian.workflow.core.dto.runtime.ActivityStatus;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.CompletionPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.StartInputs;
import io.github.leontecristian.workflow.core.dto.runtime.TaskClosureReason;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;

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
    static final String FINGERPRINT_VERSION = "wf-runtime-request-v3";
    private static final String RESULT_FINGERPRINT_VERSION = "wf-runtime-request-v4";
    static final int MAP_UTF16_BYTES = 10 * 1024 * 1024;
    static final int MAP_UTF8_BYTES = 15 * 1024 * 1024;
    public static final int HISTORY_UTF16_BYTES = 24 * 1024 * 1024;
    public static final int HISTORY_UTF8_BYTES = 36 * 1024 * 1024;
    static final int COMMAND_HISTORY_UTF16_BYTES = 64 * 1024 * 1024;
    static final int COMMAND_HISTORY_UTF8_BYTES = 96 * 1024 * 1024;
    public static final int HISTORY_RESPONSE_UTF8_BYTES = 40 * 1024 * 1024;
    public static final int LARGE_READ_UTF8_BYTES = 16 * 1024 * 1024;
    private final ObjectMapper mapper;
    public RuntimeJson() {
        mapper = com.fasterxml.jackson.databind.json.JsonMapper.builder(JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32)
                        .maxStringLength(HISTORY_UTF16_BYTES / 2).maxNumberLength(128).build()).build())
                .disable(com.fasterxml.jackson.databind.cfg.JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
                .build();
        SimpleModule numbers = new SimpleModule();
        numbers.addSerializer(io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue.class,
                new com.fasterxml.jackson.databind.JsonSerializer<>() {
                    @Override public void serialize(io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue value,
                            com.fasterxml.jackson.core.JsonGenerator generator,com.fasterxml.jackson.databind.SerializerProvider provider)
                            throws IOException { generator.writeRawValue(value.toJson()); }
                });
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
    String variablePage(io.github.leontecristian.workflow.core.dto.runtime.WorkflowVariablePage page) {
        var root=new java.util.LinkedHashMap<String,Object>(); root.put("instanceId",page.instanceId()); root.put("lockVersion",page.lockVersion());
        root.put("nextAfterKey",page.nextAfterKey()); var items=new java.util.ArrayList<Object>(); root.put("items",items);
        for (var view:page.items()) {
            var item=new java.util.LinkedHashMap<String,Object>(); items.add(item);
            item.put("key",view.key()); item.put("type",view.type().name()); item.put("initialized",view.initialized());
            Object token=view.initialized() ? view.value().value() : null;
            if(token instanceof io.github.leontecristian.workflow.core.dto.runtime.TextLiteral text) token=text.value();
            else if(token instanceof io.github.leontecristian.workflow.core.dto.runtime.BooleanLiteral bool) token=bool.value();
            else if(token==io.github.leontecristian.workflow.core.dto.runtime.NullLiteral.INSTANCE) token=null;
            item.put("value",token);
        }
        try { return mapper.writeValueAsString(root); }
        catch (IOException failure) { throw new WorkflowExecutionException(Category.HOST_CONFIGURATION); }
    }
    static boolean utf8Fits(String value,int bytes) { return value.getBytes(StandardCharsets.UTF_8).length<=bytes; }
    boolean submissionPageFits(io.github.leontecristian.workflow.core.dto.runtime.TaskSubmissionPage page) {
        try {
            return utf8Fits(mapper.writeValueAsString(page), 1024 * 1024);
        } catch (IOException failure) {
            throw new WorkflowExecutionException(Category.HOST_CONFIGURATION, failure);
        }
    }
    String encode(Object value) { return DefinitionContentCanonicalizer.canonical(mapper.valueToTree(value), HISTORY_RESPONSE_UTF8_BYTES, HISTORY_RESPONSE_UTF8_BYTES); }
    static byte[] hash(String value) { return DefinitionContentCanonicalizer.hash(value); }
    static boolean fits(String text, int bytes) {
        return text != null && text.length() * 2L <= bytes && text.getBytes(StandardCharsets.UTF_8).length <= bytes;
    }
    String evidence(Object value) {
        String json = DefinitionContentCanonicalizer.canonical(mapper.valueToTree(value),64*1024,128*1024);
        RuntimeInputs.require(fits(json, 128 * 1024), Category.LIMIT_EXCEEDED); return json;
    }
    String history(Object value) {
        try {
            return DefinitionContentCanonicalizer.canonical(mapper.valueToTree(value),HISTORY_UTF16_BYTES / 2,HISTORY_UTF8_BYTES);
        } catch (WorkflowExecutionException failure) {
            if (failure.category() == Category.LIMIT_EXCEEDED)
                throw WorkflowExecutionException.resultLimit("The complete history record exceeds its capacity.");
            throw failure;
        }
    }
    JsonNode tree(String source) {
        RuntimeInputs.require(source != null && source.length()*2L<=HISTORY_UTF16_BYTES && utf8Fits(source,HISTORY_UTF8_BYTES) && RuntimeInputs.unicode(source), Category.HOST_CONFIGURATION);
        try { return mapper.readTree(source); }
        catch (IOException failure) { throw new WorkflowExecutionException(Category.HOST_CONFIGURATION); }
    }
    String fingerprintVersion(StartInputs inputs) {
        return FINGERPRINT_VERSION;
    }
    byte[] fingerprint(StartWorkflow command, WorkflowCommandContext context) {
        return fingerprint(command,StartInputs.empty(),context);
    }
    byte[] fingerprint(StartWorkflow command, StartInputs inputs, WorkflowCommandContext context) {
        ObjectNode request = object(); request.put("definitionVersionId", Long.toString(command.definitionVersionId()));
        request.put("businessType", command.businessType()); request.put("businessId", command.businessId());
        if (command.hostStartIntent() == null) request.putNull("hostStartIntent");
        else request.set("hostStartIntent", mapper.valueToTree(command.hostStartIntent()));
        {
            var raw=object(); inputs.values().forEach((k,v) -> raw.set(k,RuntimeValueBindings.token(v))); request.set("inputs",raw);
        }
        return fingerprint(WorkflowOperation.START_WORKFLOW, context, request,fingerprintVersion(inputs));
    }
    String fingerprintVersion(CompleteWorkflowTask command) {
        return command.result() == null ? FINGERPRINT_VERSION : RESULT_FINGERPRINT_VERSION;
    }
    byte[] fingerprint(CompleteWorkflowTask command, WorkflowCommandContext context) {
        ObjectNode request = object(); request.put("instanceId", Long.toString(command.instanceId()));
        request.put("taskId", Long.toString(command.taskId())); request.put("expectedLockVersion", Long.toString(command.expectedLockVersion()));
        request.put("outcomeKey", command.outcomeKey()); request.put("comment", command.comment());
        if (command.result() != null) request.set("result", tree(command.result().toJson()));
        return fingerprint(WorkflowOperation.COMPLETE_TASK, context, request, fingerprintVersion(command));
    }
    byte[] ownershipFingerprint(WorkflowOperation operation, WorkflowCommandContext context, long instanceId,
            long taskId, long expectedLockVersion, String reason) {
        ObjectNode request = object();
        request.put("instanceId", Long.toString(instanceId));
        request.put("taskId", Long.toString(taskId));
        request.put("expectedLockVersion", Long.toString(expectedLockVersion));
        if (operation == WorkflowOperation.ADMIN_RELEASE_TASK) request.put("reason", reason);
        return fingerprint(operation, context, request);
    }

    private byte[] fingerprint(WorkflowOperation operation, WorkflowCommandContext context, ObjectNode request) {
        return fingerprint(operation,context,request,FINGERPRINT_VERSION);
    }
    private byte[] fingerprint(WorkflowOperation operation,WorkflowCommandContext context,ObjectNode request,String version) {
        ObjectNode root = object(); root.put("version", version); root.put("operation", operation.name());
        root.put("actorKind", context.actorKind().name()); root.put("actorRef", context.actorRef()); root.set("request", request);
        return hash(DefinitionContentCanonicalizer.canonical(root));
    }
    Optional<WorkflowExecutionResult> replay(WorkflowCommandReceiptEntity receipt, UUID commandId,
            WorkflowOperation operation, WorkflowCommandContext context, byte[] fingerprint) {
        return replay(receipt, commandId, operation, context, fingerprint, FINGERPRINT_VERSION);
    }

    Optional<WorkflowExecutionResult> replay(WorkflowCommandReceiptEntity receipt, UUID commandId,
            WorkflowOperation operation, WorkflowCommandContext context, byte[] fingerprint, String fingerprintVersion) {
        if (receipt == null) return Optional.empty();
        RuntimeInputs.require(receipt.getActorKind() == context.actorKind() && java.util.Objects.equals(receipt.getActorRef(), context.actorRef())
                && operation.name().equals(receipt.getOperationType()), Category.COMMAND_ID_CONFLICT);
        Short version = receipt.getResultSchemaVersion();
        boolean historical = Short.valueOf((short) 3).equals(version) || Short.valueOf((short) 4).equals(version);
        String storedFingerprintVersion = receipt.getFingerprintVersion();
        boolean hasResult = operation == WorkflowOperation.COMPLETE_TASK && RESULT_FINGERPRINT_VERSION.equals(storedFingerprintVersion);
        RuntimeInputs.require(FINGERPRINT_VERSION.equals(storedFingerprintVersion) || hasResult, Category.UNSUPPORTED_VERSION);
        RuntimeInputs.require(fingerprintVersion.equals(storedFingerprintVersion), Category.COMMAND_ID_CONFLICT);
        RuntimeInputs.require((Short.valueOf((short) 5).equals(version) || Short.valueOf((short) 6).equals(version)) || historical && !hasResult && !ownershipOperation(operation),
                Category.UNSUPPORTED_VERSION);
        RuntimeInputs.require(Arrays.equals(receipt.getRequestFingerprint(), fingerprint), Category.COMMAND_ID_CONFLICT);
        JsonNode stored = tree(receipt.getResultJson());
        requireTimeStrings(stored);
        try {
            RuntimeInputs.require(stored.path("task").isObject(), Category.HOST_CONFIGURATION);
            ObjectNode task = (ObjectNode) stored.path("task");
            if (historical) {
                RuntimeInputs.require(!task.has("assigneeRef"), Category.HOST_CONFIGURATION);
                task.putNull("assigneeRef");
            } else {
                JsonNode assignee = task.path("assigneeRef");
                RuntimeInputs.require(assignee.isNull() || assignee.isTextual(), Category.HOST_CONFIGURATION);
            }
            if (version == 3) {
                RuntimeInputs.require(!task.has("assignment") && task.path("assignedUserRef").isTextual(), Category.HOST_CONFIGURATION);
                String ref = task.remove("assignedUserRef").textValue();
                ObjectNode assignment = object(); assignment.put("kind", "USER"); assignment.put("ref", ref);
                task.set("assignment", assignment);
            }
            WorkflowExecutionResult result = mapper.treeToValue(stored, WorkflowExecutionResult.class);
            verifyResult(result, commandId, operation, receipt, historical);
            return Optional.of(result);
        } catch (IOException | IllegalArgumentException failure) { throw new WorkflowExecutionException(Category.HOST_CONFIGURATION); }
    }
    private void verifyResult(WorkflowExecutionResult result, UUID commandId, WorkflowOperation operation, WorkflowCommandReceiptEntity receipt, boolean historical) {
        RuntimeInputs.require(result != null && commandId.equals(result.commandId()) && result.operation() == operation
                && result.instance() != null && result.activity() != null && result.task() != null, Category.HOST_CONFIGURATION);
        var instance = result.instance(); var activity = result.activity(); var task = result.task();
        RuntimeInputs.require(instance.id() == receipt.getInstanceId() && instance.id() > 0 && instance.definitionVersionId() > 0 && instance.lockVersion() >= 0
                && activity.instanceId() == instance.id() && task.instanceId() == instance.id() && task.activityId() == activity.id()
                && task.taskTypeId() == activity.taskTypeId() && instance.lastHistorySequence() == result.lastHistorySequence()
                && (operation==WorkflowOperation.START_WORKFLOW ? result.firstHistorySequence()==1 : result.firstHistorySequence()>1)
                && result.firstHistorySequence()>0 && result.lastHistorySequence()>=result.firstHistorySequence() && result.lastHistorySequence()-result.firstHistorySequence()<6000
                && instance.definitionHash() != null && instance.definitionHash().matches("[0-9a-f]{64}")
                && RuntimeInputs.key(instance.definitionKey()) && instance.definitionVersion() > 0
                && task.id() > 0 && activity.id() > 0, Category.HOST_CONFIGURATION);
        RuntimeInputs.require(RuntimeInputs.key(instance.currentNodeKey()) && validTime(instance.startedAt())
                && RuntimeInputs.text(instance.startedBy(), 200, 200, true) && instance.terminationReason() == null
                && ((instance.businessType() == null && instance.businessId() == null)
                    || (RuntimeInputs.key(instance.businessType()) && RuntimeInputs.text(instance.businessId(), 200, 200, true)))
                && RuntimeInputs.key(activity.nodeKey()) && activity.activationSequence()>0 && activity.activationSequence()<=result.lastHistorySequence()
                && (operation==WorkflowOperation.START_WORKFLOW ? activity.activationSequence()>=result.firstHistorySequence() : activity.activationSequence()<result.firstHistorySequence()) && activity.taskTypeId() > 0
                && (activity.completionPolicy() == CompletionPolicy.SINGLE
                    ? activity.requiredTaskCount() == 1 && activity.contributionOutcomeKey() == null
                    : receipt.getResultSchemaVersion() == 6 && activity.completionPolicy() == CompletionPolicy.ALL
                        && activity.requiredTaskCount() >= 1 && activity.requiredTaskCount() <= 100 && RuntimeInputs.key(activity.contributionOutcomeKey()))
                && (activity.reviewRevisionRef() == null || RuntimeInputs.text(activity.reviewRevisionRef(), 200, 200, true))
                && validTime(activity.enteredAt()) && !activity.enteredAt().isBefore(instance.startedAt()) && activity.enteredAt().equals(task.createdAt())
                && RuntimeInputs.key(task.taskKey()) && task.assignment() != null && task.assignment().kind() != null
                && RuntimeInputs.text(task.assignment().ref(), 200, 200, true) && task.dueAt() == null,
                Category.HOST_CONFIGURATION);
        boolean userTarget = task.assignment().kind() == io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER;
        if (!historical) {
            RuntimeInputs.require((task.assigneeRef() == null || RuntimeInputs.text(task.assigneeRef(), 200, 200, true))
                    && (!userTarget || task.assigneeRef() == null || task.assignment().ref().equals(task.assigneeRef()))
                    && (!userTarget || task.status() != TaskStatus.OPEN || task.assignment().ref().equals(task.assigneeRef())),
                    Category.HOST_CONFIGURATION);
        }
        if (ownershipOperation(operation)) {
            RuntimeInputs.require(!userTarget && receipt.getActorKind() == ActorKind.USER
                    && result.firstHistorySequence() == result.lastHistorySequence()
                    && instance.status() == InstanceStatus.ACTIVE && activity.status() == ActivityStatus.ACTIVE
                    && task.status() == TaskStatus.OPEN && instance.currentNodeKey().equals(activity.nodeKey())
                    && instance.resultKey() == null && instance.endedAt() == null && instance.endedBy() == null
                    && activity.outcomeKey() == null && activity.closedAt() == null && activity.closedBy() == null && activity.closureReason() == null
                    && task.outcomeKey() == null && task.closedAt() == null && task.closedBy() == null && task.closureReason() == null
                    && (operation == WorkflowOperation.CLAIM_TASK ? receipt.getActorRef().equals(task.assigneeRef()) : task.assigneeRef() == null)
                    && !RuntimeViews.instant(receipt.getRecordedAt()).isBefore(task.createdAt()), Category.HOST_CONFIGURATION);
        } else if (operation == WorkflowOperation.START_WORKFLOW) {
            RuntimeInputs.require(instance.status() == InstanceStatus.ACTIVE
                    && activity.status() == ActivityStatus.ACTIVE
                    && task.status() == TaskStatus.OPEN
                    && (userTarget || task.assigneeRef() == null)
                    && instance.startedAt().equals(activity.enteredAt())
                    && instance.currentNodeKey().equals(activity.nodeKey()) && instance.resultKey() == null
                    && instance.endedAt() == null && instance.endedBy() == null && activity.outcomeKey() == null
                    && activity.closedAt() == null && activity.closedBy() == null && activity.closureReason() == null
                    && task.outcomeKey() == null && task.closedAt() == null && task.closedBy() == null && task.closureReason() == null
                    && instance.startedBy().equals(receipt.getActorRef()) && instance.startedAt().equals(RuntimeViews.instant(receipt.getRecordedAt())),
                    Category.HOST_CONFIGURATION);
        } else if (activity.status() == ActivityStatus.ACTIVE && activity.completionPolicy() == CompletionPolicy.ALL) {
            RuntimeInputs.require(receipt.getResultSchemaVersion() == 6 && instance.status() == InstanceStatus.ACTIVE
                    && instance.currentNodeKey().equals(activity.nodeKey()) && instance.resultKey() == null
                    && instance.endedAt() == null && instance.endedBy() == null
                    && activity.requiredTaskCount() > 1 && activity.outcomeKey() == null && activity.closedAt() == null
                    && activity.closedBy() == null && activity.closureReason() == null
                    && task.status() == TaskStatus.COMPLETED && task.closureReason() == TaskClosureReason.TASK_OUTCOME
                    && activity.contributionOutcomeKey().equals(task.outcomeKey()) && validTime(task.closedAt())
                    && !task.closedAt().isBefore(task.createdAt()) && receipt.getActorKind() == ActorKind.USER
                    && receipt.getActorRef().equals(task.closedBy())
                    && (task.assigneeRef() == null || task.assigneeRef().equals(task.closedBy()))
                    && (!userTarget || task.assignment().ref().equals(task.closedBy()))
                    && task.closedAt().equals(RuntimeViews.instant(receipt.getRecordedAt())),Category.HOST_CONFIGURATION);
        } else {
            boolean terminal = instance.status() == InstanceStatus.COMPLETED;
            RuntimeInputs.require((terminal || instance.status() == InstanceStatus.ACTIVE)
                    && activity.status() == ActivityStatus.COMPLETED
                    && task.status() == TaskStatus.COMPLETED
                    && (terminal ? RuntimeInputs.key(instance.resultKey()) && validTime(instance.endedAt())
                        && instance.endedAt().equals(activity.closedAt()) && receipt.getActorRef().equals(instance.endedBy())
                        : instance.resultKey() == null && instance.endedAt() == null && instance.endedBy() == null
                            && !instance.currentNodeKey().equals(activity.nodeKey()))
                    && validTime(activity.closedAt()) && !activity.closedAt().isBefore(activity.enteredAt())
                    && activity.closedAt().equals(task.closedAt())
                    && receipt.getActorKind() == ActorKind.USER
                    && receipt.getActorRef().equals(activity.closedBy())
                    && (task.assigneeRef() == null || task.assigneeRef().equals(task.closedBy()))
                    && receipt.getActorRef().equals(task.closedBy()) && (task.assignment().kind() != io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER
                        || receipt.getActorRef().equals(task.assignment().ref()))
                    && "TASK_OUTCOME".equals(activity.closureReason())
                    && task.closureReason() == TaskClosureReason.TASK_OUTCOME
                    && RuntimeInputs.key(task.outcomeKey()) && task.outcomeKey().equals(activity.outcomeKey())
                    && activity.closedAt().equals(RuntimeViews.instant(receipt.getRecordedAt())), Category.HOST_CONFIGURATION);
        }
    }
    private static boolean ownershipOperation(WorkflowOperation operation) {
        return operation == WorkflowOperation.CLAIM_TASK || operation == WorkflowOperation.RELEASE_TASK
                || operation == WorkflowOperation.ADMIN_RELEASE_TASK;
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
