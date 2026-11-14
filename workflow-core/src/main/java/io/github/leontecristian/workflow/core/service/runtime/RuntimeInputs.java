package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.HostStartIntent;
import io.github.leontecristian.workflow.core.dto.runtime.StartWorkflow;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

final class RuntimeInputs {
    private RuntimeInputs() { }
    static void require(boolean valid, Category category) { if (!valid) throw new WorkflowExecutionException(category); }
    static boolean isWellFormedUnicode(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }
    static boolean isValidText(String text, int points, int units, boolean nonblank) {
        return text != null && isWellFormedUnicode(text) && text.length() <= units && text.codePointCount(0, text.length()) <= points
                && (!nonblank || !text.isBlank());
    }
    static boolean isValidKey(String value) { return value != null && value.matches("[A-Za-z][A-Za-z0-9._-]{0,99}"); }
    static LocalDateTime validateContextAndGetOccurredAtUtc(WorkflowCommandContext context) {
        require(context != null && context.actorKind() != null && isValidText(context.actorRef(), 200, 200, true)
                && context.occurredAt() != null && context.occurredAt().getNano() % 100 == 0
                && (context.correlationId() == null || isValidText(context.correlationId(), 100, 100, true)), Category.INVALID_INPUT);
        try {
            LocalDateTime time = LocalDateTime.ofInstant(context.occurredAt(), ZoneOffset.UTC);
            require(time.getYear() >= 1 && time.getYear() <= 9999, Category.INVALID_INPUT);
            return time;
        } catch (java.time.DateTimeException failure) { throw new WorkflowExecutionException(Category.INVALID_INPUT, failure); }
    }
    static void validateStartCommand(StartWorkflow command, WorkflowCommandContext context) {
        validateContextAndGetOccurredAtUtc(context);
        require(command != null && command.commandId() != null, Category.INVALID_INPUT);
        int selectors = (command.workflowKey() == null ? 0 : 1) + (command.definitionId() == null ? 0 : 1)
                + (command.definitionVersionId() == null ? 0 : 1);
        require(selectors == 1 && (command.workflowKey() == null || isValidKey(command.workflowKey()))
                && (command.definitionId() == null || command.definitionId() > 0)
                && (command.definitionVersionId() == null || command.definitionVersionId() > 0), Category.INVALID_INPUT);
        require((command.businessType() == null && command.businessId() == null)
                || (isValidKey(command.businessType()) && isValidText(command.businessId(), 200, 200, true)), Category.INVALID_INPUT);
        HostStartIntent intent = command.hostStartIntent();
        require(intent == null || (intent.fingerprintVersion() != null && intent.fingerprintVersion().length() <= 40
                && isValidKey(intent.fingerprintVersion()) && intent.fingerprintSha256() != null
                && intent.fingerprintSha256().matches("[0-9a-f]{64}")), Category.INVALID_INPUT);
    }
    static void validateCompleteTaskCommand(CompleteWorkflowTask command, WorkflowCommandContext context) {
        validateContextAndGetOccurredAtUtc(context);
        require(command != null && command.commandId() != null && command.instanceId() > 0 && command.taskId() > 0
                && command.expectedLockVersion() >= 0 && isValidKey(command.outcomeKey())
                && (command.comment() == null || isValidText(command.comment(), 2000, 2000, false)), Category.INVALID_INPUT);
        // Actor-kind/assignment and required-comment checks intentionally follow successful receipt lookup.
    }
    static void validatePageRequest(long cursor, int limit) { require(cursor >= 0 && limit >= 1 && limit <= 100, Category.INVALID_INPUT); }
    static void validatePositiveId(long id) { require(id > 0, Category.INVALID_INPUT); }
}
