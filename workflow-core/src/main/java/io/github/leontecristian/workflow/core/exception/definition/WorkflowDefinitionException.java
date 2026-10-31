package io.github.leontecristian.workflow.core.exception.definition;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionDiagnostic;
import java.util.List;
public class WorkflowDefinitionException extends RuntimeException {
    public enum Category { INVALID_INPUT, NOT_FOUND, CONFLICT, INVALID_STATE, UNSUPPORTED_CANONICALIZATION, HOST_CONFIGURATION }
    private final Category category;
    private final List<DefinitionDiagnostic> diagnostics;
    public WorkflowDefinitionException(Category category, List<DefinitionDiagnostic> diagnostics) {
        super("Workflow definition operation failed: " + category);
        this.category = category;
        this.diagnostics = List.copyOf(diagnostics);
    }
    public WorkflowDefinitionException(Category category, Throwable cause) {
        super("Workflow definition operation failed: " + category, cause);
        this.category = category;
        this.diagnostics = List.of();
    }
    public Category category() { return category; }
    public List<DefinitionDiagnostic> diagnostics() { return diagnostics; }
}
