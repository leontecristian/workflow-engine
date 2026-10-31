package io.github.leontecristian.workflow.core.service.definition;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionCapabilities;
/** Host extension certifying deployed implementations; never invoked to execute business logic. */
public interface DefinitionCapabilityProvider {
    DefinitionCapabilities snapshot();
}
