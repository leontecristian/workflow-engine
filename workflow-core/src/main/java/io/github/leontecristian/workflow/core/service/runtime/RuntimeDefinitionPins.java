package io.github.leontecristian.workflow.core.service.runtime;

import io.github.leontecristian.workflow.core.dto.definition.document.ExecutableDefinition;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowInstanceEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryId;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowHistoryRepository;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionRuntimeReader;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import org.springframework.stereotype.Component;

/** The immutable start fact retains the pinned hash; no extra instance column is invented. */
@Component
public class RuntimeDefinitionPins {
    private final WorkflowDefinitionRuntimeReader definitions;
    private final WorkflowHistoryRepository history;
    private final RuntimeJson json;
    public RuntimeDefinitionPins(WorkflowDefinitionRuntimeReader definitions, WorkflowHistoryRepository history, RuntimeJson json) {
        this.definitions = definitions; this.history = history; this.json = json;
    }
    ExecutableDefinition load(WorkflowInstanceEntity instance) {
        var definition = definitions.loadPinned(instance.getDefinitionVersionId());
        verify(instance, definition.versionId(), definition.key(), definition.version(), definition.contentHash());
        return definition;
    }
    io.github.leontecristian.workflow.core.dto.definition.document.PinnedDefinitionIdentity identity(WorkflowInstanceEntity instance) {
        var definition = definitions.readIdentity(instance.getDefinitionVersionId());
        verify(instance, definition.versionId(), definition.key(), definition.version(), definition.contentHash());
        return definition;
    }
    private void verify(WorkflowInstanceEntity instance, long versionId, String key, int version, String hash) {
        var fact = history.findById(new WorkflowHistoryId(instance.getId(), 1L))
                .orElseThrow(() -> new WorkflowExecutionException(Category.HOST_CONFIGURATION));
        RuntimeInputs.require(fact.getDetailsSchemaVersion() == 1, Category.UNSUPPORTED_VERSION);
        var details = json.tree(fact.getDetailsJson());
        RuntimeInputs.require("INSTANCE_STARTED".equals(fact.getEventType()) && fact.getActivityId() == null && fact.getTaskId() == null
                && java.util.Objects.equals(fact.getActorRef(), instance.getStartedBy()) && fact.getOccurredAt().equals(instance.getStartedAt())
                && details.isObject() && details.size() == 7 && details.path("definitionHash").isTextual()
                && details.path("definitionVersionId").isTextual() && details.path("definitionKey").isTextual()
                && details.has("businessType") && details.has("businessId") && details.has("hostStartIntent")
                && (instance.getBusinessType() == null ? details.get("businessType").isNull()
                    : details.get("businessType").isTextual() && instance.getBusinessType().equals(details.get("businessType").textValue()))
                && (instance.getBusinessId() == null ? details.get("businessId").isNull()
                    : details.get("businessId").isTextual() && instance.getBusinessId().equals(details.get("businessId").textValue()))
                && hash.equals(details.path("definitionHash").asText())
                && Long.toString(versionId).equals(details.path("definitionVersionId").asText())
                && key.equals(details.path("definitionKey").asText())
                && details.path("definitionVersion").isNumber() && details.path("definitionVersion").canConvertToInt()
                && details.path("definitionVersion").decimalValue().compareTo(java.math.BigDecimal.valueOf(version)) == 0,
                Category.HOST_CONFIGURATION);
    }
}
