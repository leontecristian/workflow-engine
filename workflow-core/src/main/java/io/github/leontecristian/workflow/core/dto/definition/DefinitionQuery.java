package io.github.leontecristian.workflow.core.dto.definition;
/** Continue in the same order with the returned cursor; zero starts either ordering. */
public record DefinitionQuery(long afterId, int limit, DefinitionOrder order) {
    public DefinitionQuery(long afterId, int limit) { this(afterId, limit, DefinitionOrder.ASC); }
    public DefinitionQuery() { this(0, 50); }
}
