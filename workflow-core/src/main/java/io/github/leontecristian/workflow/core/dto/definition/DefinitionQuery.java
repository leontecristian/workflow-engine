package io.github.leontecristian.workflow.core.dto.definition;
public record DefinitionQuery(long afterId, int limit) {
    public DefinitionQuery() { this(0, 50); }
}
