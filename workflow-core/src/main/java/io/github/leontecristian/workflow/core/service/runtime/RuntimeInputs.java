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
    static boolean unicode(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }
    static boolean text(String text, int points, int units, boolean nonblank) {
        return text != null && unicode(text) && text.length() <= units && text.codePointCount(0, text.length()) <= points
                && (!nonblank || !text.isBlank());
    }
    static boolean key(String value) { return value != null && value.matches("[A-Za-z][A-Za-z0-9._-]{0,99}"); }
    static LocalDateTime context(WorkflowCommandContext context) {
        require(context != null && context.actorKind() != null && text(context.actorRef(), 200, 200, true)
                && context.occurredAt() != null && context.occurredAt().getNano() % 100 == 0
                && (context.correlationId() == null || text(context.correlationId(), 100, 100, true)), Category.INVALID_INPUT);
        try {
            LocalDateTime time = LocalDateTime.ofInstant(context.occurredAt(), ZoneOffset.UTC);
            require(time.getYear() >= 1 && time.getYear() <= 9999, Category.INVALID_INPUT);
            return time;
        } catch (java.time.DateTimeException failure) { throw new WorkflowExecutionException(Category.INVALID_INPUT, failure); }
    }
    static void start(StartWorkflow command, WorkflowCommandContext context) {
        context(context);
        require(command != null && command.commandId() != null && command.definitionVersionId() > 0, Category.INVALID_INPUT);
        require((command.businessType() == null && command.businessId() == null)
                || (key(command.businessType()) && text(command.businessId(), 200, 200, true)), Category.INVALID_INPUT);
        HostStartIntent intent = command.hostStartIntent();
        require(intent == null || (intent.fingerprintVersion() != null && intent.fingerprintVersion().length() <= 40
                && key(intent.fingerprintVersion()) && intent.fingerprintSha256() != null
                && intent.fingerprintSha256().matches("[0-9a-f]{64}")), Category.INVALID_INPUT);
    }
    static void complete(CompleteWorkflowTask command, WorkflowCommandContext context) {
        context(context);
        require(command != null && command.commandId() != null && command.instanceId() > 0 && command.taskId() > 0
                && command.expectedLockVersion() >= 0 && key(command.outcomeKey())
                && (command.comment() == null || text(command.comment(), 2000, 2000, false)), Category.INVALID_INPUT);
        // Actor-kind/assignment and required-comment checks intentionally follow successful receipt lookup.
    }
    static void page(long cursor, int limit) { require(cursor >= 0 && limit >= 1 && limit <= 100, Category.INVALID_INPUT); }
    static void id(long id) { require(id > 0, Category.INVALID_INPUT); }
}
