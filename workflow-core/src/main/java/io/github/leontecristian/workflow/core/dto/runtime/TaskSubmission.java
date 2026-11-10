package io.github.leontecristian.workflow.core.dto.runtime;

/** Detached runtime contract. Host authorization and transaction completion remain required. */
public record TaskSubmission(
        long taskId,
        long activityId,
        long instanceId,
        long historySequence,
        java.util.UUID commandId,
        ActorKind actorKind,
        String actorRef,
        java.time.Instant occurredAt,
        String outcomeKey,
        String comment,
        java.util.Map<String, String> inputs,
        WorkflowJsonValue result) {
    public TaskSubmission {
        inputs = java.util.Map.copyOf(inputs);
        if (!inputs.isEmpty()) throw new IllegalArgumentException("Inputs must be empty");
        if (result != null) CompleteWorkflowTask.requireResultObject(result);
    }

    public TaskSubmission(long taskId, long activityId, long instanceId, long historySequence,
            java.util.UUID commandId, ActorKind actorKind, String actorRef, java.time.Instant occurredAt,
            String outcomeKey, String comment, java.util.Map<String, String> inputs) {
        this(taskId, activityId, instanceId, historySequence, commandId, actorKind, actorRef, occurredAt,
                outcomeKey, comment, inputs, null);
    }

}
