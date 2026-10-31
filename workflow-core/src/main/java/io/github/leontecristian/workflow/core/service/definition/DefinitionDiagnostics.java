package io.github.leontecristian.workflow.core.service.definition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionDiagnostic;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionValidationReport;
import io.github.leontecristian.workflow.core.dto.definition.DiagnosticSeverity;
import io.github.leontecristian.workflow.core.dto.definition.DiagnosticStage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class DefinitionDiagnostics {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final List<DefinitionDiagnostic> details = new ArrayList<>();
    private boolean failed;
    private boolean omitted;

    void error(String code, DiagnosticStage stage, String message, String pointer) {
        failed = true;
        if (details.size() >= 100) { omitted = true; return; }
        String safePointer = pointer;
        if (pointer != null && pointer.length() > 1000) { safePointer = null; omitted = true; }
        DefinitionDiagnostic detail = new DefinitionDiagnostic("WF_DEF_" + code, stage, DiagnosticSeverity.ERROR,
                message, safePointer, null, null);
        if (!fits(json(detail), 8192)) { omitted = true; return; }
        details.add(detail);
    }

    boolean failed() { return failed; }

    DefinitionValidationReport report() {
        while (true) {
            DefinitionValidationReport report = new DefinitionValidationReport(!failed, details, omitted);
            if (fits(json(report), 128 * 1024)) return report;
            details.remove(details.size() - 1);
            omitted = true;
        }
    }

    static String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Cannot encode definition evidence", failure); }
    }

    static boolean fits(String value, int bytes) {
        return value.length() * 2L <= bytes && value.getBytes(StandardCharsets.UTF_8).length <= bytes;
    }
}
