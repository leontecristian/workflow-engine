package io.github.leontecristian.workflow.core.dto.definition;
public record DefinitionVersionQuery(
        long definitionId,
        DefinitionStatus status,
        long afterId,
        int limit) {
    public DefinitionVersionQuery(long definitionId) { this(definitionId, null, 0, 50); }
}
