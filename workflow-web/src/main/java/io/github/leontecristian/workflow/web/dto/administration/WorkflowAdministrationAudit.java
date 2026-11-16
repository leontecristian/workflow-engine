package io.github.leontecristian.workflow.web.dto.administration;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;

/** Changed-state evidence. Snapshots are core TaskTypeView, DefinitionView or DefinitionVersionSummary;
 * creation has null before and deletion has null after. Hosts own serialization and storage.
 */
public record WorkflowAdministrationAudit(
        String operation, String entityKind, long entityId, DefinitionManagementContext context,
        String correlationId, Object before, Object after) {}
