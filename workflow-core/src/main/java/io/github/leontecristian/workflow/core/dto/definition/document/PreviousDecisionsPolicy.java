package io.github.leontecristian.workflow.core.dto.definition.document;

import java.util.List;
import java.util.Objects;

/** Immutable resolved receiving-task visibility policy from its pinned definition. */
public record PreviousDecisionsPolicy(Mode mode, List<String> nodeKeys) {
    public enum Mode { NONE, ALL_PREVIOUS, SELECTED }
    public static final PreviousDecisionsPolicy NONE = new PreviousDecisionsPolicy(Mode.NONE, List.of());
    public static final PreviousDecisionsPolicy ALL_PREVIOUS = new PreviousDecisionsPolicy(Mode.ALL_PREVIOUS, List.of());

    public PreviousDecisionsPolicy {
        Objects.requireNonNull(mode);
        nodeKeys = List.copyOf(nodeKeys);
        if (mode == Mode.SELECTED ? nodeKeys.isEmpty() || nodeKeys.stream().distinct().count() != nodeKeys.size()
                : !nodeKeys.isEmpty()) throw new IllegalArgumentException("Invalid previous-decision policy");
    }
}
