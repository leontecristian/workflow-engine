package io.github.leontecristian.workflow.core.dto.definition;
import java.util.List;
public record DefinitionVersionPage(List<DefinitionVersionSummary> items, Long nextAfterId) {
    public DefinitionVersionPage { items = List.copyOf(items); }
}
