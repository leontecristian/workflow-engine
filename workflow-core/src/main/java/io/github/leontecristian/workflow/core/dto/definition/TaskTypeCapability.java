package io.github.leontecristian.workflow.core.dto.definition;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** A host bean describing a deployed task type and its supported outcome keys. */
public record TaskTypeCapability(String taskType, Set<String> supportedOutcomes) {
    public TaskTypeCapability {
        // Invalid metadata is reported by core when the registry is consumed.
        if (supportedOutcomes != null)
            supportedOutcomes = Collections.unmodifiableSet(new LinkedHashSet<>(supportedOutcomes));
    }
}
