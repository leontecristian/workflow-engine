package io.github.leontecristian.workflow.core.dto.runtime;

import java.util.List;

public record PreviousDecisionPage(List<PreviousDecision> items, Long nextAfterSequence) {
    public PreviousDecisionPage { items = List.copyOf(items); }
}
