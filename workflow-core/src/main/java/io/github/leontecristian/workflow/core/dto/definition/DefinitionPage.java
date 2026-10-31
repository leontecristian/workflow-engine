package io.github.leontecristian.workflow.core.dto.definition;
import java.util.List;
public record DefinitionPage(List<DefinitionView> items, Long nextAfterId) {
    public DefinitionPage { items = List.copyOf(items); }
}
