package io.github.leontecristian.workflow.core.service.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.CancelWorkflow;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowOperation;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowCommandReceiptEntity;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CancellationReceiptUnitTest {
    private final RuntimeJson json = new RuntimeJson();
    private final UUID id = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private final CancelWorkflow command = new CancelWorkflow(id, 1, 3, " stop exactly ");
    private final WorkflowCommandContext context = new WorkflowCommandContext(ActorKind.USER, "admin",
            Instant.parse("2026-01-03T00:00:00Z"), "attempt");

    @Test void fingerprintUsesExistingV3EnvelopeAndExactCancellationFields() {
        String canonical = "{\"actorKind\":\"USER\",\"actorRef\":\"admin\",\"operation\":\"CANCEL_WORKFLOW\","
                + "\"request\":{\"expectedLockVersion\":\"3\",\"instanceId\":\"1\",\"reason\":\" stop exactly \"},"
                + "\"version\":\"wf-runtime-request-v3\"}";
        byte[] hash = json.fingerprint(command, context);
        assertArrayEquals(RuntimeJson.hash(canonical), hash);
        assertArrayEquals(hash, json.fingerprint(new CancelWorkflow(UUID.randomUUID(), 1, 3, command.reason()),
                new WorkflowCommandContext(ActorKind.USER, "admin", Instant.parse("2027-01-01T00:00:00Z"), "other")));
        for (var changed : List.of(new CancelWorkflow(id, 2, 3, command.reason()),
                new CancelWorkflow(id, 1, 4, command.reason()), new CancelWorkflow(id, 1, 3, "stop exactly"))) {
            assertFalse(Arrays.equals(hash, json.fingerprint(changed, context)));
        }
        assertFalse(Arrays.equals(hash, json.fingerprint(command, new WorkflowCommandContext(ActorKind.USER,
                "other", context.occurredAt(), null))));
        assertFalse(Arrays.equals(hash, json.fingerprint(command, new WorkflowCommandContext(ActorKind.SYSTEM,
                "admin", context.occurredAt(), null))));
    }

    @Test void frozenSingleAndPartialOrFullAllResultsReplayWithoutCurrentState() {
        assertTrue(json.cancellationReplay(null, command, context).isEmpty());
        for (int required : new int[]{1, 2, 100}) {
            for (int cancelled : required == 1 ? new int[]{1} : new int[]{1, required}) {
                ObjectNode root = snapshot();
                ObjectNode activity = (ObjectNode) root.path("activity");
                if (required > 1) activity.put("completionPolicy", "ALL").put("requiredTaskCount", required)
                        .put("contributionOutcomeKey", "APPROVE");
                root.put("cancelledTaskCount", cancelled).put("lastHistorySequence", Long.toString(11L + cancelled));
                ((ObjectNode) root.path("instance")).put("lastHistorySequence", Long.toString(11L + cancelled));
                String frozen = json.encode(root);
                var receipt = receipt(frozen, (short) 7, RuntimeJson.FINGERPRINT_VERSION);
                var result = json.cancellationReplay(receipt, command, context).orElseThrow();
                assertEquals(cancelled, result.cancelledTaskCount());
                assertEquals(command.reason(), result.instance().terminationReason());
                assertEquals(frozen, receipt.getResultJson());
            }
        }
    }

    @Test void changedRequestAndActorConflictAndUnknownVersionsFailExplicitly() {
        var receipt = receipt(json.encode(snapshot()), (short) 7, RuntimeJson.FINGERPRINT_VERSION);
        for (var changed : List.of(new CancelWorkflow(id, 1, 4, command.reason()), new CancelWorkflow(id, 2, 3, command.reason()),
                new CancelWorkflow(id, 1, 3, "different"))) {
            assertCategory(Category.COMMAND_ID_CONFLICT, () -> json.cancellationReplay(receipt, changed, context));
        }
        assertCategory(Category.COMMAND_ID_CONFLICT, () -> json.cancellationReplay(receipt, command,
                new WorkflowCommandContext(ActorKind.USER, "other", context.occurredAt(), null)));
        assertCategory(Category.UNSUPPORTED_VERSION, () -> json.cancellationReplay(
                receipt(json.encode(snapshot()), (short) 6, RuntimeJson.FINGERPRINT_VERSION), command, context));
        assertCategory(Category.UNSUPPORTED_VERSION, () -> json.cancellationReplay(
                receipt(json.encode(snapshot()), (short) 7, "unknown"), command, context));
        assertCategory(Category.UNSUPPORTED_VERSION, () -> json.replay(receipt, id, WorkflowOperation.CANCEL_WORKFLOW,
                context, json.fingerprint(command, context)));
    }

    @Test void cancellationSnapshotRejectsMalformedOrInconsistentEvidence() {
        List<Consumer<ObjectNode>> corruptions = List.of(
                r -> r.put("commandId", UUID.randomUUID().toString()),
                r -> r.put("operation", "COMPLETE_TASK"), r -> r.put("unexpected", true),
                r -> r.put("cancelledTaskCount", 0), r -> r.put("cancelledTaskCount", 101),
                r -> r.put("cancelledTaskCount", 1.5), r -> r.put("cancelledTaskCount", "1"),
                r -> r.put("firstHistorySequence", "0"), r -> r.put("lastHistorySequence", "13"),
                r -> r.put("lastHistorySequence", 12), r -> r.putNull("activity"),
                r -> instance(r).put("id", "2"), r -> instance(r).put("definitionHash", "bad"),
                r -> instance(r).put("definitionVersionId", "0"), r -> instance(r).put("definitionVersion", 0),
                r -> instance(r).put("lockVersion", "3"), r -> instance(r).put("businessType", "ONLY_HALF"),
                r -> instance(r).put("status", "ACTIVE"), r -> instance(r).put("resultKey", "CANCELLED"),
                r -> instance(r).put("terminationReason", "stop exactly"), r -> instance(r).putNull("terminationReason"),
                r -> instance(r).put("endedBy", "other"), r -> instance(r).put("startedBy", ""),
                r -> instance(r).put("endedAt", "2026-01-03T00:00:00Z"), r -> instance(r).put("endedAt", 123),
                r -> instance(r).put("startedAt", "2027-01-01T00:00:00Z"),
                r -> instance(r).put("lastHistorySequence", "11"),
                r -> activity(r).put("instanceId", "2"), r -> activity(r).put("id", "0"),
                r -> activity(r).put("nodeKey", "OTHER"), r -> activity(r).put("activationSequence", "10"),
                r -> activity(r).put("status", "COMPLETED"), r -> activity(r).put("outcomeKey", "APPROVE"),
                r -> activity(r).put("closureReason", "TASK_OUTCOME"), r -> activity(r).put("closedBy", "other"),
                r -> activity(r).put("closedAt", "2026-01-02T00:00:00.000000001Z"),
                r -> activity(r).put("enteredAt", "2026-01-03T00:00:00Z"),
                r -> activity(r).put("requiredTaskCount", 2), r -> activity(r).put("reviewRevisionRef", ""),
                r -> activity(r).put("completionPolicy", "ALL"), r -> activity(r).put("taskTypeId", "0"));
        for (var corruption : corruptions) {
            ObjectNode root = snapshot();
            corruption.accept(root);
            assertCategory(Category.HOST_CONFIGURATION, () -> json.cancellationReplay(
                    receipt(json.encode(root), (short) 7, RuntimeJson.FINGERPRINT_VERSION), command, context));
        }
        String valid = json.encode(snapshot());
        for (String malformed : List.of(valid + " {}", valid.replace("\"cancelledTaskCount\":1", "\"cancelledTaskCount\":1,\"cancelledTaskCount\":1"))) {
            assertCategory(Category.HOST_CONFIGURATION, () -> json.cancellationReplay(
                    receipt(malformed, (short) 7, RuntimeJson.FINGERPRINT_VERSION), command, context));
        }
        var wrongId = new WorkflowCommandReceiptEntity(UUID.randomUUID(), 1, "CANCEL_WORKFLOW", ActorKind.USER,
                "admin", json.fingerprint(command, context), RuntimeJson.FINGERPRINT_VERSION,
                LocalDateTime.parse("2026-01-02T00:00:00"), valid, (short) 7);
        assertCategory(Category.HOST_CONFIGURATION, () -> json.cancellationReplay(wrongId, command, context));
    }

    private static ObjectNode instance(ObjectNode root) { return (ObjectNode) root.path("instance"); }
    private static ObjectNode activity(ObjectNode root) { return (ObjectNode) root.path("activity"); }
    private void assertCategory(Category expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(WorkflowExecutionException.class, action).category());
    }
    private WorkflowCommandReceiptEntity receipt(String result, short schema, String fingerprintVersion) {
        return new WorkflowCommandReceiptEntity(id, 1, "CANCEL_WORKFLOW", ActorKind.USER, "admin",
                json.fingerprint(command, context), fingerprintVersion, LocalDateTime.parse("2026-01-02T00:00:00"), result, schema);
    }
    private ObjectNode snapshot() {
        return (ObjectNode) json.tree("""
                {"commandId":"12345678-1234-1234-1234-123456789abc","operation":"CANCEL_WORKFLOW",
                 "cancelledTaskCount":1,"firstHistorySequence":"10","lastHistorySequence":"12",
                 "instance":{"id":"1","definitionVersionId":"42","definitionKey":"review","definitionVersion":1,
                   "definitionHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                   "businessType":null,"businessId":null,"currentNodeKey":"REVIEW","status":"CANCELLED","resultKey":null,
                   "startedAt":"2026-01-01T00:00:00Z","startedBy":"starter","endedAt":"2026-01-02T00:00:00Z",
                   "endedBy":"admin","terminationReason":" stop exactly ","lastHistorySequence":"12","lockVersion":"4"},
                 "activity":{"id":"2","instanceId":"1","nodeKey":"REVIEW","activationSequence":"8","taskTypeId":"3",
                   "completionPolicy":"SINGLE","contributionOutcomeKey":null,"requiredTaskCount":1,"reviewRevisionRef":null,
                   "status":"CANCELLED","outcomeKey":null,"enteredAt":"2026-01-01T12:00:00Z",
                   "closedAt":"2026-01-02T00:00:00Z","closedBy":"admin","closureReason":"INSTANCE_CANCELLED"}}
                """);
    }
}
