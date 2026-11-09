package io.github.leontecristian.workflow.core.service.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowTaskEntity;

/** Strict interpretation of retained creation evidence without rewriting historical bytes. */
final class RuntimeTaskCreationEvidence {
    private RuntimeTaskCreationEvidence() { }

    static boolean matches(WorkflowHistoryEntity history, WorkflowTaskEntity task, RuntimeJson json) {
        JsonNode evidence = json.tree(history.getDetailsJson());
        if (!evidence.isObject() || evidence.size() != (history.getDetailsSchemaVersion() == 4 ? 3 : 2) || !evidence.path("taskKey").isTextual()
                || !task.getTaskKey().equals(evidence.path("taskKey").textValue())) return false;
        TaskAssignmentTarget target;
        if (history.getDetailsSchemaVersion() == 2 && evidence.path("assignedUserRef").isTextual()) {
            target = new TaskAssignmentTarget(AssignmentTargetKind.USER, evidence.path("assignedUserRef").textValue());
        } else if (history.getDetailsSchemaVersion() == 3 || history.getDetailsSchemaVersion() == 4) {
            JsonNode assignment = evidence.path("assignment");
            if (!assignment.isObject() || assignment.size() != 2 || !assignment.path("kind").isTextual()
                    || !assignment.path("ref").isTextual()) return false;
            AssignmentTargetKind kind;
            try { kind = AssignmentTargetKind.valueOf(assignment.path("kind").textValue()); }
            catch (IllegalArgumentException failure) { return false; }
            target = new TaskAssignmentTarget(kind, assignment.path("ref").textValue());
            if (history.getDetailsSchemaVersion() == 4) {
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
