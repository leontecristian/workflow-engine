package io.github.leontecristian.workflow.core.dto.runtime;

import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;

/** Detached runtime contract. Host authorization and transaction completion remain required. */
public record CompleteWorkflowTask(
        java.util.UUID commandId,
        long instanceId,
        long taskId,
        long expectedLockVersion,
        String outcomeKey,
        String comment,
        WorkflowJsonValue result) {
    public CompleteWorkflowTask(java.util.UUID commandId, long instanceId, long taskId, long expectedLockVersion,
            String outcomeKey, String comment) {
        this(commandId, instanceId, taskId, expectedLockVersion, outcomeKey, comment, null);
    }

    public CompleteWorkflowTask {
        if (result != null) requireResultObject(result);
    }

    /** Parse a supplied result token with exact values. */
    public static WorkflowJsonValue parseResult(String json) {
        try {
            var result = WorkflowJsonValue.parse(json);
            requireResultObject(result);
            return result;
        } catch (WorkflowExecutionException failure) {
            if (failure.category() == Category.LIMIT_EXCEEDED && failure.safeMessage() == null) {
                throw WorkflowExecutionException.resultLimit("The result exceeds the JSON size, depth, or item limits.");
            }
            throw failure;
        }
    }

    static void requireResultObject(WorkflowJsonValue result) {
        if (!result.toJson().startsWith("{")) throw new WorkflowExecutionException(Category.INVALID_INPUT);
        if (result.toJson().length() > 32768) {
            throw WorkflowExecutionException.resultLimit("The result exceeds 64 KiB of canonical UTF-16 JSON.");
        }
    }
}
