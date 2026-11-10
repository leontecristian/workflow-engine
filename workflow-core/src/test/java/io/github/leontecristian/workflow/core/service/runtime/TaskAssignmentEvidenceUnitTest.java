package io.github.leontecristian.workflow.core.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Binding;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.HumanInput;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.NullLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowActivityEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowTaskEntity;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskAssignmentEvidenceUnitTest {
    private final RuntimeJson json = new RuntimeJson();
    private final LocalDateTime time = LocalDateTime.of(2026, 1, 1, 0, 0);

    private WorkflowHistoryEntity history(short version, String details) {
        return new WorkflowHistoryEntity(new WorkflowHistorySummary(1, 3, "TASK_CREATED", UUID.randomUUID(), null,
                ActorKind.USER, "starter", time.toInstant(ZoneOffset.UTC), 2L, 3L, null, null, null, version), details);
    }

    @Test void legacyUserAndCurrentTypedEvidenceMatchOnlyTheExactTarget() {
        for (var kind : AssignmentTargetKind.values()) {
            var task = new WorkflowTaskEntity(2, "review", new TaskAssignmentTarget(kind, "finance "), time);
            String details = json.evidence(java.util.Map.of("taskKey", "review", "assignment", task.getAssignment()));
            assertTrue(RuntimeTaskCreationEvidence.matches(history((short)3, details), task, json));
            String legacy = "{\"taskKey\":\"review\",\"assignedUserRef\":\"finance \"}";
            assertEquals(kind == AssignmentTargetKind.USER,
                    RuntimeTaskCreationEvidence.matches(history((short)2, legacy), task, json));
            for (String invalid : new String[]{details.replace("finance ", "finance"),
                    details.replace("\"taskKey\"", "\"unexpected\""), details.replace("\"kind\"", "\"unknown\""),
                    details.substring(0, details.length()-1) + ",\"extra\":true}"})
                assertFalse(RuntimeTaskCreationEvidence.matches(history((short)3, invalid), task, json));
            assertFalse(RuntimeTaskCreationEvidence.matches(history((short)4, details), task, json));
        }
    }

    @Test void sharedClosureKeepsTargetAndActualActorWhileUserStillRequiresIdentity() {
        for (var kind : AssignmentTargetKind.values()) {
            var target = new TaskAssignmentTarget(kind, "finance");
            var task = new WorkflowTaskEntity(2, "review", target, time);
            if (kind == AssignmentTargetKind.USER) {
                assertThrows(IllegalStateException.class, () -> task.complete("APPROVE", time, "alice"));
                task.complete("APPROVE", time, "finance");
            } else task.complete("APPROVE", time, "alice");
            assertEquals(target, task.getAssignment());
            assertEquals(kind == AssignmentTargetKind.USER ? "finance" : "alice", task.getClosedBy());
            assertThrows(IllegalStateException.class, () -> task.complete("REJECT", time, "alice"));
        }
    }

    @Test void retainedInputsUsePinnedNamesTypesAndNullabilityAndPreserveExactTokens() {
        var inputs = Map.of(
                "maybe", new HumanInput(new Binding("variable", "maybe", null), VariableValueType.LONG, true),
                "date", new HumanInput(new Binding("variable", "date", null), VariableValueType.DATE, false),
                "original", new HumanInput(new Binding("submission", "earlier", null), VariableValueType.JSON, false));
        var values = Map.of(
                "maybe", new WorkflowValue(VariableValueType.LONG, NullLiteral.INSTANCE),
                "date", new WorkflowValue(VariableValueType.DATE, new TextLiteral("2026-11-10")),
                "earlier", new WorkflowValue(VariableValueType.JSON,
                        WorkflowJsonValue.parse("{\"number\":9007199254740993,\"decimal\":0.123456789000}")));
        var task = mock(WorkflowTaskEntity.class);
        when(task.getId()).thenReturn(3L);
        when(task.getActivityId()).thenReturn(2L);
        when(task.getTaskKey()).thenReturn("review");
        when(task.getCreatedAt()).thenReturn(time);
        when(task.getAssignment()).thenReturn(new TaskAssignmentTarget(AssignmentTargetKind.GROUP, "finance"));
        var activity = mock(WorkflowActivityEntity.class);
        when(activity.getId()).thenReturn(2L);
        when(activity.getInstanceId()).thenReturn(1L);
        when(activity.getActivationSequence()).thenReturn(2L);
        var legacy = "{\"taskKey\":\"review\",\"assignment\":{\"kind\":\"GROUP\",\"ref\":\"finance\"},\"assigneeRef\":null}";
        var details = (com.fasterxml.jackson.databind.node.ObjectNode) json.tree(legacy);
        var snapshot = RuntimeTaskCreationEvidence.capture(inputs, input -> values.get(input.binding().key()), json);
        details.set("inputs", snapshot);
        String encoded = json.encode(details);
        var result = RuntimeTaskCreationEvidence.read(history((short) 5, encoded), task, activity, inputs, json);
        assertEquals(Map.of("maybe", values.get("maybe"), "date", values.get("date"), "original", values.get("earlier")), result);
        assertEquals(Map.of(), RuntimeTaskCreationEvidence.read(history((short) 4, legacy), task, activity, Map.of(), json));
        assertEquals(Category.HOST_CONFIGURATION, assertThrows(WorkflowExecutionException.class,
                () -> RuntimeTaskCreationEvidence.read(history((short) 4, legacy), task, activity, inputs, json)).category());
        for (String corrupt : new String[]{encoded.replace("\"maybe\"", "\"unknown\""), encoded.replace("\"DATE\"", "\"STRING\""),
                encoded.replace("\"2026-11-10\"", "null"), encoded.replace("{\"decimal\":0.123456789000,\"number\":9007199254740993}", "[]"),
                encoded.replace("\"value\":null", "\"value\":1")}) {
            assertEquals(Category.HOST_CONFIGURATION, assertThrows(WorkflowExecutionException.class,
                    () -> RuntimeTaskCreationEvidence.read(history((short) 5, corrupt), task, activity, inputs, json)).category());
        }
    }
}
