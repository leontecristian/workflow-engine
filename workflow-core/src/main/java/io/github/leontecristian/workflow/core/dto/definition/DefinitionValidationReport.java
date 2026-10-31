package io.github.leontecristian.workflow.core.dto.definition;
import java.util.List;
public record DefinitionValidationReport(boolean valid, List<DefinitionDiagnostic> diagnostics, boolean detailsOmitted) {
    public DefinitionValidationReport { diagnostics = List.copyOf(diagnostics); }
}
