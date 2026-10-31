package io.github.leontecristian.workflow.core.dto.definition;
import java.util.Set;
public record TaskTypeCapability(String taskType, Set<String> supportedOutcomes) {
    public TaskTypeCapability { supportedOutcomes = Set.copyOf(supportedOutcomes); }
}
