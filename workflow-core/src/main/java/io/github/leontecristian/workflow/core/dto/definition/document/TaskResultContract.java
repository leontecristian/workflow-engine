package io.github.leontecristian.workflow.core.dto.definition.document;

import java.util.Objects;

/** Pinned result policy and detached, exact schema text. */
public record TaskResultContract(Mode mode, String schemaJson) {
    public enum Mode { FORBIDDEN, OPTIONAL, REQUIRED }

    public static final TaskResultContract FORBIDDEN = new TaskResultContract(Mode.FORBIDDEN, null);

    public TaskResultContract {
        Objects.requireNonNull(mode);
        if ((mode == Mode.FORBIDDEN) != (schemaJson == null))
            throw new IllegalArgumentException("Result schema does not match its mode.");
    }
}
