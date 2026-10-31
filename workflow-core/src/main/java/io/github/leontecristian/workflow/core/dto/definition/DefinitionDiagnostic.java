package io.github.leontecristian.workflow.core.dto.definition;

/** Immutable in-process definition contract; the host owns authorization and transport mapping. */
public record DefinitionDiagnostic(
        String code,
        DiagnosticStage stage,
        DiagnosticSeverity severity,
        String message,
        String pointer,
        String elementKey,
        String relatedPointer) {
}
