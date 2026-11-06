package io.github.leontecristian.workflow.core.service.definition;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionDiagnostic;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.definition.DiagnosticSeverity;
import io.github.leontecristian.workflow.core.dto.definition.DiagnosticStage;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

final class DefinitionInputs {
    private DefinitionInputs() { }

    static void require(boolean condition) {
        if (!condition) throw error("INVALID_IDENTITY", "Invalid definition operation input.");
    }

    static boolean unicode(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }

    static boolean text(String value, int points, int bytes, boolean nonblank) {
        return value != null && unicode(value) && (!nonblank || !value.isBlank())
                && value.codePointCount(0, value.length()) <= points && value.length() * 2L <= bytes;
    }

    static boolean key(String value) {
        return value != null && value.matches("[A-Za-z][A-Za-z0-9._-]{0,99}");
    }

    static void metadata(String name, String description) {
        require(text(name, 200, 400, true));
        require(description == null || text(description, 2000, 4000, false));
    }

    static LocalDateTime context(DefinitionManagementContext context) {
        require(context != null && text(context.actorRef(), 200, 400, true)
                && context.occurredAt() != null && context.occurredAt().getNano() % 100 == 0);
        LocalDateTime utc;
        try { utc = LocalDateTime.ofInstant(context.occurredAt(), ZoneOffset.UTC); }
        catch (java.time.DateTimeException failure) { throw error("INVALID_IDENTITY", "Invalid management time."); }
        require(utc.getYear() >= 1 && utc.getYear() <= 9999);
        return utc;
    }

    static WorkflowDefinitionException error(String code, String message) {
        return error(code, message, null);
    }

    static WorkflowDefinitionException error(String code, String message, String pointer) {
        return new WorkflowDefinitionException(WorkflowDefinitionException.Category.INVALID_INPUT,
                List.of(new DefinitionDiagnostic("WF_DEF_" + code, DiagnosticStage.PARSE,
                        DiagnosticSeverity.ERROR, message, pointer, null, null)));
    }
}
