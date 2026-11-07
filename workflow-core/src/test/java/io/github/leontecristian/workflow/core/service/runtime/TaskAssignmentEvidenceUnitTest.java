package io.github.leontecristian.workflow.core.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowTaskEntity;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
}
